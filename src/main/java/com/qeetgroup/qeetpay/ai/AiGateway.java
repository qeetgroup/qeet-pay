package com.qeetgroup.qeetpay.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The AI gateway substrate (PRD §6.4 Human-Oversight & Safety Matrix; TAD §8.2/§8.3). Every future AI
 * feature calls {@link #evaluate} — no feature talks to a model directly. Each call, in order:
 *
 * <ol>
 *   <li><b>Permission-aware.</b> Runs under the caller's merchant RLS scope, never widening access.
 *   <li><b>No raw PII / PAN to the model.</b> The raw input is masked ({@link PiiMasker}) before any
 *       model call; only a SHA-256 hash of the raw input and the masked form are ever persisted.
 *   <li><b>Fail-closed to a deterministic path.</b> If the model errors/times out, or returns a
 *       stale / ambiguous / low-confidence result, the caller-supplied deterministic fallback is used.
 *   <li><b>Human-in-the-loop on money-affecting types.</b> A money-affecting decision that has not
 *       been human-reviewed also falls back to the deterministic path and is flagged
 *       {@code requiresHumanReview} — the model result is never auto-applied.
 *   <li><b>Auditable.</b> Every request + decision is written to the append-only {@code ai.ai_decision}
 *       table and emitted to the outbox ({@code ai.decision.recorded}) for qeet-logs.
 * </ol>
 */
@Service
public class AiGateway {

    /** Outbox event type drained to {@code pay.{merchant_id}.events.ai.decision.recorded}. */
    public static final String EVENT_TYPE = "ai.decision.recorded";

    private final AiDecisionRepository decisions;
    private final AiModelClient modelClient;
    private final PiiMasker piiMasker;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public AiGateway(
            AiDecisionRepository decisions,
            AiModelClient modelClient,
            PiiMasker piiMasker,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.decisions = decisions;
        this.modelClient = modelClient;
        this.piiMasker = piiMasker;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates an AI request under the §6.4 safety matrix, persists the decision audit row, emits the
     * outbox event, and returns the resolved decision.
     *
     * @param request the feature request (see {@link AiRequest})
     * @param deterministicFallback the deterministic path to use when the model cannot be trusted or a
     *     money-affecting decision awaits human review; its result is authoritative and never sent to a
     *     model. Must not be {@code null}.
     */
    @Transactional
    public AiDecisionResult evaluate(AiRequest request, Supplier<String> deterministicFallback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deterministicFallback, "deterministicFallback");
        UUID merchantId = request.merchantId();
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId is required");
        }
        if (request.feature() == null || request.feature().isBlank()) {
            throw new IllegalArgumentException("feature is required");
        }

        // (c) Permission-aware: bind the DB session to the caller's merchant so RLS scopes everything.
        merchantScope.apply(merchantId);

        String model =
                (request.model() == null || request.model().isBlank())
                        ? modelClient.modelId()
                        : request.model();

        // (a) Mask PII/PAN before any model call; keep only a hash of the raw input for traceability.
        String rawInput = request.input() == null ? "" : request.input();
        String maskedInput = piiMasker.mask(rawInput);
        String inputHash = sha256(rawInput);

        boolean moneyAffecting = request.moneyAffecting() || AiFeature.isMoneyAffecting(request.feature());
        boolean humanReviewed = request.humanReviewed();
        boolean requiresHumanReview = moneyAffecting && !humanReviewed;

        String outputJson;
        double confidence;
        boolean fellBack;

        try {
            AiModelClient.Inference inference =
                    modelClient.infer(request.feature(), model, maskedInput, request.scopes());
            boolean unusable =
                    inference.stale()
                            || inference.ambiguous()
                            || inference.confidence() < request.minConfidence();
            // (b) Fail-closed: unusable model result, OR a money-affecting decision awaiting a human.
            if (unusable || requiresHumanReview) {
                outputJson = fallbackJson(deterministicFallback);
                confidence = 1.0; // the deterministic rule is authoritative
                fellBack = true;
            } else {
                outputJson = inference.outputJson();
                confidence = inference.confidence();
                fellBack = false;
            }
        } catch (RuntimeException modelError) {
            // Model timed out / errored → deterministic fallback (fail-closed).
            outputJson = fallbackJson(deterministicFallback);
            confidence = 1.0;
            fellBack = true;
        }

        AiDecision decision =
                decisions.save(
                        new AiDecision(
                                merchantId,
                                request.feature(),
                                model,
                                inputHash,
                                maskedInput,
                                outputJson,
                                confidence,
                                humanReviewed,
                                fellBack));
        // (d) Auditable: emit to the outbox in the same transaction for qeet-logs.
        outbox.enqueue(merchantId, EVENT_TYPE, auditJson(decision, moneyAffecting, requiresHumanReview, request.scopes()));

        return new AiDecisionResult(
                decision.getId(), outputJson, confidence, humanReviewed, fellBack, requiresHumanReview);
    }

    // ── Reads (audit) ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AiDecision> listDecisions(UUID merchantId) {
        merchantScope.apply(merchantId);
        return decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public AiDecision getDecision(UUID merchantId, UUID decisionId) {
        merchantScope.apply(merchantId);
        return decisions
                .findById(decisionId)
                .filter(d -> d.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new AiDecisionNotFoundException("no ai decision " + decisionId));
    }

    /** Gateway health — the active model and whether the offline sandbox stand-in is in use. */
    public GatewayHealth health() {
        boolean sandbox = modelClient instanceof SandboxAiModelClient;
        return new GatewayHealth("ok", modelClient.modelId(), sandbox);
    }

    public record GatewayHealth(String status, String model, boolean sandbox) {}

    // ── Internals ────────────────────────────────────────────────────────────

    private static String fallbackJson(Supplier<String> deterministicFallback) {
        String out = deterministicFallback.get();
        return out == null ? "{}" : out;
    }

    private String auditJson(
            AiDecision decision, boolean moneyAffecting, boolean requiresHumanReview, java.util.Set<String> scopes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decisionId", decision.getId().toString());
        body.put("feature", decision.getFeature());
        body.put("model", decision.getModel());
        body.put("inputHash", decision.getInputHash());
        body.put("confidence", decision.getConfidence());
        body.put("moneyAffecting", moneyAffecting);
        body.put("humanReviewed", decision.isHumanReviewed());
        body.put("requiresHumanReview", requiresHumanReview);
        body.put("fellBack", decision.isFellBack());
        body.put("scopes", scopes == null ? List.of() : List.copyOf(scopes));
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise ai decision event", e);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
