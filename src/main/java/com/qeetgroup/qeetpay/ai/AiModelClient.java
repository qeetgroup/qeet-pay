package com.qeetgroup.qeetpay.ai;

import java.util.Set;

/**
 * Pluggable inference backend behind {@link AiGateway} — the offline {@link SandboxAiModelClient}
 * stand-in, or a future real client (e.g. the Claude model family, TAD §8.2 / ADR-007). The gateway
 * is the only caller: it masks PII first and never passes raw identifiers here.
 *
 * <p>To add a real client, register a bean named {@code liveAiModelClient} implementing this
 * interface; the sandbox stands aside automatically ({@code @ConditionalOnMissingBean}). Implementations
 * MUST throw (any {@link RuntimeException}, e.g. {@link AiModelUnavailableException}) on timeout/error
 * so the gateway can fall back deterministically.
 */
public interface AiModelClient {

    /**
     * Runs inference over already-masked input.
     *
     * @param feature the AI feature key (e.g. {@code "gst.classification"})
     * @param model the model id to use ({@link #modelId()} when the caller did not pin one)
     * @param maskedInput the prompt/input with PII already redacted by {@link PiiMasker}
     * @param scopes the caller's RBAC scopes, passed through for permission-aware inference
     * @return the model's inference; throws on timeout/error
     */
    Inference infer(String feature, String model, String maskedInput, Set<String> scopes);

    /** The default model id this client serves — recorded on the audit row and reported by health. */
    String modelId();

    /**
     * A single model inference result. {@code stale}/{@code ambiguous}/low {@code confidence} all
     * cause the gateway to fail closed to the deterministic path (PRD §6.4).
     */
    record Inference(String outputJson, double confidence, boolean stale, boolean ambiguous) {

        /** A clean, fully-confident result (not stale, not ambiguous). */
        public static Inference confident(String outputJson) {
            return new Inference(outputJson, 1.0, false, false);
        }

        /** A clean result at the given confidence (not stale, not ambiguous). */
        public static Inference of(String outputJson, double confidence) {
            return new Inference(outputJson, confidence, false, false);
        }
    }
}
