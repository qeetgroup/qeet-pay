package com.qeetgroup.qeetpay.dunning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The AI dunning strategist (PRD Module 04.2 "Smart Retry" + 04.5 "Explainable Dunning"). Turns the
 * flat, category-only {@link FailureClassifier} heuristic into a personalised, explainable recovery plan
 * — channel order, payday-aware timing, and message tone — computed through the {@link AiGateway}.
 *
 * <p>How it uses the gateway (TAD §8.2 / PRD §6.4):
 *
 * <ul>
 *   <li>A feature vector (failure class + engagement signals) is the gateway {@code input}; the gateway
 *       masks any PII in it before the {@code SandboxAiModelClient} offline stand-in (or a future real
 *       model) sees it, and audits the decision to {@code ai.ai_decision} + the outbox.
 *   <li>The <b>deterministic {@link FailureClassifier} heuristic is the fail-closed fallback</b>: if the
 *       model errors / times out / is low-confidence, the gateway returns {@code fellBack == true} and we
 *       serve the plain heuristic plan ({@code aiAssisted == false}).
 *   <li>The <b>money-affecting decision (retry vs. stop) is never the model's</b> — {@code retryable}
 *       always comes from the deterministic classifier. Only the advisory communication plan (channels /
 *       timing / tone) is personalised by the AI path, so the model is genuinely exercised rather than
 *       always gated behind human review.
 * </ul>
 */
@Component
public class AiRetryStrategy {

    /** Advisory feature key (deliberately not a money-affecting {@code AiFeature}; see class doc). */
    static final String FEATURE = "dunning.retry_strategy";

    private static final int PAYDAY_BUFFER_HOURS = 6;   // retry ~6h after funds are expected to land
    private static final int MAX_DELAY_HOURS = 168;     // never defer a retry more than a week
    private static final double LOW_ENGAGEMENT = 0.30;  // below this, broaden the nudge across channels
    private static final List<String> ESCALATION = List.of("WHATSAPP", "SMS", "EMAIL");

    private final AiGateway gateway;
    private final FailureClassifier classifier;
    private final ObjectMapper objectMapper;

    public AiRetryStrategy(AiGateway gateway, FailureClassifier classifier, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.classifier = classifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Recommends a personalised, explainable retry plan for a failed collection. Routes through the AI
     * gateway (masked + audited); falls back to the deterministic {@link FailureClassifier} heuristic
     * when the model cannot be trusted.
     */
    public AiRetryPlan recommend(
            UUID merchantId, String failureCode, EngagementSignals signals, Set<String> scopes) {
        EngagementSignals sig = signals == null ? EngagementSignals.unknown() : signals;
        RetryRecommendation base = classifier.classify(failureCode);
        AiRetryPlan deterministic = deterministicPlan(base);

        AiRequest request =
                new AiRequest(
                        merchantId,
                        FEATURE,
                        null,
                        featureVector(failureCode, base, sig),
                        false, // advisory comms plan; the money decision (retryable) stays deterministic
                        false,
                        scopes == null ? Set.of() : scopes,
                        0.5);

        AiDecisionResult result = gateway.evaluate(request, () -> toJson(deterministic));

        if (result.fellBack()) {
            List<String> reasons = new ArrayList<>(deterministic.reasons());
            reasons.add(
                    "AI model unavailable or low-confidence — used the deterministic FailureClassifier "
                            + "heuristic (fail-closed).");
            return new AiRetryPlan(
                    deterministic.category(),
                    deterministic.retryable(),
                    deterministic.recommendedDelayHours(),
                    deterministic.channelOrder(),
                    deterministic.messageTone(),
                    reasons,
                    false,
                    result.decisionId());
        }
        return enhancedPlan(base, sig, result.decisionId());
    }

    // ── Plan construction ──────────────────────────────────────────────────────

    /** The plain heuristic plan — category only, no engagement personalisation (the fallback). */
    private AiRetryPlan deterministicPlan(RetryRecommendation base) {
        List<String> channels = splitChannels(base.recommendedChannels());
        List<String> reasons = new ArrayList<>();
        reasons.add(base.rationale());
        return new AiRetryPlan(
                base.category(),
                base.retryable(),
                base.recommendedDelayHours(),
                channels,
                toneFor(base.category()),
                reasons,
                false,
                null);
    }

    /** The AI path — personalises timing/channels/tone from engagement signals. */
    private AiRetryPlan enhancedPlan(RetryRecommendation base, EngagementSignals sig, UUID decisionId) {
        FailureCategory category = base.category();
        int delay = paydayAwareDelay(base, sig);
        List<String> channels = personalisedChannels(base, sig);
        String tone = personalisedTone(category, sig);

        List<String> reasons = new ArrayList<>();
        reasons.add(base.rationale());
        boolean fundsRelated =
                category == FailureCategory.INSUFFICIENT_FUNDS || category == FailureCategory.LIMIT_EXCEEDED;
        if (fundsRelated && sig.paydayKnown()) {
            reasons.add(
                    "Timed to retry ~"
                            + PAYDAY_BUFFER_HOURS
                            + "h after the payer's next payday (in "
                            + sig.daysUntilPayday()
                            + " day(s)) so funds are likely available.");
        }
        if (!channels.isEmpty() && hasPreferred(sig)) {
            reasons.add(
                    "Led with the payer's preferred channel ("
                            + sig.preferredChannel().trim().toUpperCase(Locale.ROOT)
                            + ").");
        }
        if (!channels.isEmpty() && sig.engagementScore() < LOW_ENGAGEMENT) {
            reasons.add(
                    "Low recent engagement (score "
                            + sig.engagementScore()
                            + ") — broadened the nudge across WhatsApp, SMS and email.");
        }
        reasons.add("Message tone set to '" + tone + "' for a " + category.name() + " failure.");
        reasons.add(
                "AI-personalised via the AI gateway (audited decision "
                        + decisionId
                        + "); the retry/stop decision itself remains the deterministic classifier's.");

        return new AiRetryPlan(
                category, base.retryable(), delay, channels, tone, reasons, true, decisionId);
    }

    /** Payday-aware timing: for funds-related failures with a known payday, wait until just after it. */
    private int paydayAwareDelay(RetryRecommendation base, EngagementSignals sig) {
        FailureCategory category = base.category();
        boolean fundsRelated =
                category == FailureCategory.INSUFFICIENT_FUNDS || category == FailureCategory.LIMIT_EXCEEDED;
        if (base.retryable() && fundsRelated && sig.paydayKnown()) {
            int paydayDelay = sig.daysUntilPayday() * 24 + PAYDAY_BUFFER_HOURS;
            return Math.max(1, Math.min(paydayDelay, MAX_DELAY_HOURS));
        }
        return base.recommendedDelayHours();
    }

    /** Orders channels: preferred-first, broadened when engagement is low; silent stays silent. */
    private List<String> personalisedChannels(RetryRecommendation base, EngagementSignals sig) {
        List<String> ordered = new ArrayList<>(splitChannels(base.recommendedChannels()));
        if (ordered.isEmpty()) {
            return ordered; // deliberately silent (e.g. transient technical) — keep it silent
        }
        LinkedHashSet<String> set = new LinkedHashSet<>(ordered);
        if (sig.engagementScore() < LOW_ENGAGEMENT) {
            set.addAll(ESCALATION);
        }
        if (hasPreferred(sig)) {
            String pref = sig.preferredChannel().trim().toUpperCase(Locale.ROOT);
            LinkedHashSet<String> reordered = new LinkedHashSet<>();
            reordered.add(pref);
            reordered.addAll(set);
            set = reordered;
        }
        return new ArrayList<>(set);
    }

    private String personalisedTone(FailureCategory category, EngagementSignals sig) {
        String tone = toneFor(category);
        if (category != FailureCategory.RISK_DECLINE && sig.engagementScore() < LOW_ENGAGEMENT) {
            return "urgent";
        }
        return tone;
    }

    private static String toneFor(FailureCategory category) {
        return switch (category) {
            case INSUFFICIENT_FUNDS -> "empathetic";
            case LIMIT_EXCEEDED -> "helpful";
            case TECHNICAL_DECLINE -> "reassuring";
            case CUSTOMER_ACTION -> "instructive";
            case MANDATE_ISSUE -> "instructive";
            case RISK_DECLINE -> "formal";
            case UNKNOWN -> "neutral";
        };
    }

    private static boolean hasPreferred(EngagementSignals sig) {
        return sig.preferredChannel() != null && !sig.preferredChannel().isBlank();
    }

    private static List<String> splitChannels(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return out;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        return out;
    }

    // ── Serialisation (feature vector in, audited plan out) ─────────────────────

    private String featureVector(String failureCode, RetryRecommendation base, EngagementSignals sig) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("failureCode", failureCode);
        v.put("category", base.category().name());
        v.put("baseRetryable", base.retryable());
        v.put("baseDelayHours", base.recommendedDelayHours());
        v.put("baseChannels", base.recommendedChannels());
        v.put("daysUntilPayday", sig.daysUntilPayday());
        v.put("engagementScore", sig.engagementScore());
        v.put("preferredChannel", sig.preferredChannel());
        v.put("preferredLanguage", sig.preferredLanguage());
        v.put("contactHint", sig.customerContactHint());
        return toJson(v);
    }

    private String toJson(AiRetryPlan plan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", plan.category().name());
        m.put("retryable", plan.retryable());
        m.put("recommendedDelayHours", plan.recommendedDelayHours());
        m.put("channelOrder", plan.channelOrder());
        m.put("messageTone", plan.messageTone());
        m.put("reasons", plan.reasons());
        return toJson(m);
    }

    private String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise ai retry payload", e);
        }
    }
}
