package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HSN/SAC classification (PRD Module 05, TAD §7 + §8.2). Given a product/service description, suggests
 * ranked HSN/SAC codes with GST rate + confidence and an explanation, routed through the
 * {@link AiGateway} §6.4 safety matrix (PII masking, audit trail, outbox event, fail-closed).
 *
 * <p>The gateway's caller-supplied deterministic fallback is {@link HsnCatalog} — a curated keyword →
 * HSN/SAC map. In the shipped offline configuration the model is the {@code SandboxAiModelClient}
 * stand-in, whose stub output carries no HSN, so the classifier uses the deterministic candidates; when
 * a real {@code liveAiModelClient} returns a structured {@code suggestions} payload, that is preferred.
 * Either way a low-confidence primary suggestion is flagged {@code requiresReview} for a human.
 */
@Service
public class HsnClassifier {

    /** {@link com.qeetgroup.qeetpay.ai.AiFeature#GST_CLASSIFICATION} key — advisory, not money-affecting. */
    static final String FEATURE = "gst.classification";

    /** Below this the primary suggestion is flagged for human review (PRD §6.4). */
    static final double REVIEW_THRESHOLD = 0.60;

    private final AiGateway gateway;
    private final HsnClassificationRepository cache;
    private final MerchantScope merchantScope;
    private final ObjectMapper objectMapper;

    public HsnClassifier(
            AiGateway gateway,
            HsnClassificationRepository cache,
            MerchantScope merchantScope,
            ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.cache = cache;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClassificationResult classify(UUID merchantId, String description, Set<String> scopes) {
        merchantScope.apply(merchantId);
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }

        String queryHash = sha256(normalize(description));
        Optional<HsnClassification> cached = cache.findByMerchantIdAndQueryHash(merchantId, queryHash);
        if (cached.isPresent()) {
            HsnClassification row = cached.get();
            row.recordHit();
            cache.save(row);
            return readResult(row.getResultJson());
        }

        // Deterministic candidates — the authoritative fallback fed to the gateway.
        HsnCatalog.Ranked ranked = HsnCatalog.classify(description);
        String fallbackJson = fallbackJson(ranked);

        AiDecisionResult decision =
                gateway.evaluate(
                        new AiRequest(
                                merchantId, FEATURE, null, description, false, false,
                                scopes == null ? Set.of() : scopes, REVIEW_THRESHOLD),
                        () -> fallbackJson);

        // Prefer a real model's structured output; otherwise use the deterministic candidates.
        Optional<List<HsnSuggestion>> modelSuggestions =
                decision.fellBack() ? Optional.empty() : parseSuggestions(decision.outputJson());
        boolean usedModel = modelSuggestions.isPresent();
        List<HsnSuggestion> suggestions = usedModel ? modelSuggestions.get() : ranked.suggestions();
        HsnSuggestion primary = suggestions.get(0);

        boolean requiresReview = primary.confidence() < REVIEW_THRESHOLD || decision.requiresHumanReview();

        ClassificationResult result =
                new ClassificationResult(
                        decision.decisionId().toString(),
                        gateway.health().model(),
                        usedModel ? "model" : "deterministic",
                        decision.fellBack(),
                        requiresReview,
                        primary.confidence(),
                        ranked.explanation(),
                        suggestions);

        cache.save(
                new HsnClassification(
                        merchantId, queryHash, writeResult(result), primary.hsnSac(), primary.gstRate(),
                        primary.confidence(), requiresReview, decision.decisionId()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<HsnClassification> recent(UUID merchantId) {
        merchantScope.apply(merchantId);
        return cache.findAll().stream().filter(c -> c.getMerchantId().equals(merchantId)).toList();
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private String fallbackJson(HsnCatalog.Ranked ranked) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("suggestions", ranked.suggestions());
        body.put("explanation", ranked.explanation());
        body.put("confidence", ranked.suggestions().get(0).confidence());
        return write(body);
    }

    /** Parses a {@code {"suggestions":[...]}} payload (a real model's output shape); empty if absent. */
    private Optional<List<HsnSuggestion>> parseSuggestions(String outputJson) {
        try {
            JsonNode root = objectMapper.readTree(outputJson);
            JsonNode arr = root.get("suggestions");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            List<HsnSuggestion> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n.get("hsnSac") == null) {
                    return Optional.empty();
                }
                out.add(
                        new HsnSuggestion(
                                n.get("hsnSac").asText(),
                                n.hasNonNull("kind") ? n.get("kind").asText() : HsnCatalog.KIND_HSN,
                                n.hasNonNull("gstRate") ? n.get("gstRate").asInt() : 0,
                                n.hasNonNull("confidence") ? n.get("confidence").asDouble() : 0.0,
                                n.hasNonNull("label") ? n.get("label").asText() : ""));
            }
            return Optional.of(out);
        } catch (RuntimeException | JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private ClassificationResult readResult(String json) {
        try {
            return objectMapper.readValue(json, ClassificationResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to read cached classification", e);
        }
    }

    private String writeResult(ClassificationResult result) {
        return write(result);
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise classification", e);
        }
    }

    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
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
