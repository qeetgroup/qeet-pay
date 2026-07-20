package com.qeetgroup.qeetpay.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ML-style provider scorer (PRD Module 07.3 "Orchestration ML"). Ranks acquirers by a predicted
 * <em>auth-rate × (1 − cost)</em> objective computed through the {@link AiGateway} (offline
 * {@code SandboxAiModelClient} stand-in over the scorecard features), falling back to the current
 * deterministic {@link ProviderScorer} scorecard ranking when the model cannot be trusted.
 *
 * <p>This is an <b>advisory</b> ranking + explanation surface. The money-moving route selection for a
 * real payment stays in the deterministic {@link ProviderRoutingService#chooseProviderName}; this scorer
 * only recommends and explains, so the model path is genuinely exercised rather than always gated behind
 * human review. Every call is masked + audited by the gateway.
 */
@Service
public class AiProviderScorer {

    /** Advisory feature key (deliberately not a money-affecting {@code AiFeature}; see class doc). */
    static final String FEATURE = "orchestration.ranking";
    private static final double DEGRADED_FACTOR = 0.9; // multiplicative penalty for a DEGRADED provider

    private final AiGateway gateway;
    private final ProviderScorecardRepository scorecards;
    private final MerchantScope merchantScope;
    private final ObjectMapper objectMapper;

    public AiProviderScorer(
            AiGateway gateway,
            ProviderScorecardRepository scorecards,
            MerchantScope merchantScope,
            ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.scorecards = scorecards;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
    }

    /**
     * Ranks {@code candidates} best-first. When the AI gateway's model path is usable, ranks by the
     * predicted auth-rate × (1 − cost) objective; otherwise falls back to the deterministic
     * {@link ProviderScorer} scorecard ranking.
     *
     * @param merchantId the tenant (RLS scope)
     * @param candidates the acquirers to rank (order is the default preference)
     * @param complianceContext short GST/place-of-supply context folded into the feature vector, or null
     * @param scopes the caller's RBAC scopes, passed through the gateway
     */
    @Transactional
    public ProviderRanking rank(
            UUID merchantId, List<String> candidates, String complianceContext, Set<String> scopes) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one candidate provider is required");
        }
        merchantScope.apply(merchantId);

        Map<String, ProviderScorecard> byName = new LinkedHashMap<>();
        for (ProviderScorecard card : scorecards.findByMerchantId(merchantId)) {
            if (candidates.contains(card.getProvider())) {
                byName.put(card.getProvider(), card);
            }
        }

        AiRequest request =
                new AiRequest(
                        merchantId,
                        FEATURE,
                        null,
                        featureVector(merchantId, candidates, byName, complianceContext),
                        false, // advisory ranking; money-moving routing stays deterministic
                        false,
                        scopes == null ? Set.of() : scopes,
                        0.5);

        AiDecisionResult result =
                gateway.evaluate(request, () -> deterministicJson(merchantId, candidates, byName));

        boolean aiAssisted = !result.fellBack();
        List<RankedProvider> ranked = new ArrayList<>();
        for (String name : candidates) {
            ProviderScorecard card = byName.getOrDefault(name, new ProviderScorecard(merchantId, name));
            ranked.add(toRanked(name, card, aiAssisted));
        }
        ranked.sort(Comparator.comparingDouble(RankedProvider::score).reversed());

        String recommended = ranked.isEmpty() ? null : ranked.get(0).provider();
        String method = aiAssisted ? "ai-predicted" : "deterministic-scorecard";
        return new ProviderRanking(ranked, recommended, method, aiAssisted, result.decisionId());
    }

    // ── Scoring ────────────────────────────────────────────────────────────────

    /** A DOWN provider is ranked last; use a finite sentinel so the JSON response stays valid. */
    private static final double EXCLUDED_SCORE = -1_000_000.0;

    private RankedProvider toRanked(String name, ProviderScorecard card, boolean aiAssisted) {
        double authRate = card.authRate();
        int costBps = card.getCostBps();
        ProviderHealth health = card.getHealth();
        double raw = aiAssisted ? predictedScore(card) : ProviderScorer.score(card, ProviderScorer.Weights.DEFAULT);
        double score = Double.isFinite(raw) ? raw : EXCLUDED_SCORE;
        String why = aiAssisted ? aiWhy(authRate, costBps, health) : deterministicWhy(authRate, costBps, health, score);
        return new RankedProvider(name, authRate, costBps, health.name(), score, why);
    }

    /** Predicted objective: auth-rate × (1 − cost), with a DOWN provider excluded and DEGRADED penalised. */
    private static double predictedScore(ProviderScorecard card) {
        if (card.getHealth() == ProviderHealth.DOWN) {
            return Double.NEGATIVE_INFINITY;
        }
        double predicted = card.authRate() * (1.0 - card.getCostBps() / 10_000.0);
        return card.getHealth() == ProviderHealth.DEGRADED ? predicted * DEGRADED_FACTOR : predicted;
    }

    private static String aiWhy(double authRate, int costBps, ProviderHealth health) {
        if (health == ProviderHealth.DOWN) {
            return "Provider is DOWN — excluded from routing.";
        }
        String base =
                String.format(
                        Locale.ROOT,
                        "predicted auth-rate %.1f%% × (1 − %.2f%% cost) = %.4f",
                        authRate * 100.0,
                        costBps / 100.0,
                        predictedRaw(authRate, costBps));
        return health == ProviderHealth.DEGRADED ? base + "; DEGRADED (×0.9 penalty)" : base + "; HEALTHY";
    }

    private static double predictedRaw(double authRate, int costBps) {
        return authRate * (1.0 - costBps / 10_000.0);
    }

    private static String deterministicWhy(double authRate, int costBps, ProviderHealth health, double score) {
        if (health == ProviderHealth.DOWN) {
            return "Provider is DOWN — excluded from routing.";
        }
        return String.format(
                Locale.ROOT,
                "deterministic scorecard: auth-rate %.1f%%, cost %.2f%%, health %s → score %.4f",
                authRate * 100.0,
                costBps / 100.0,
                health.name(),
                score);
    }

    // ── Serialisation ───────────────────────────────────────────────────────────

    private String featureVector(
            UUID merchantId,
            List<String> candidates,
            Map<String, ProviderScorecard> byName,
            String complianceContext) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("merchantId", merchantId.toString());
        v.put("candidates", candidates);
        v.put("complianceContext", complianceContext);
        List<Map<String, Object>> providers = new ArrayList<>();
        for (String name : candidates) {
            ProviderScorecard card = byName.get(name);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("provider", name);
            p.put("authRate", card == null ? 1.0 : card.authRate());
            p.put("costBps", card == null ? 0 : card.getCostBps());
            p.put("attempts", card == null ? 0 : card.getAttempts());
            p.put("health", card == null ? ProviderHealth.HEALTHY.name() : card.getHealth().name());
            providers.add(p);
        }
        v.put("providers", providers);
        return toJson(v);
    }

    private String deterministicJson(
            UUID merchantId, List<String> candidates, Map<String, ProviderScorecard> byName) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : candidates) {
            ProviderScorecard card = byName.getOrDefault(name, new ProviderScorecard(merchantId, name));
            double raw = ProviderScorer.score(card, ProviderScorer.Weights.DEFAULT);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("provider", name);
            p.put("score", Double.isFinite(raw) ? raw : EXCLUDED_SCORE);
            out.add(p);
        }
        return toJson(Map.of("method", "deterministic-scorecard", "providers", out));
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise provider ranking payload", e);
        }
    }
}
