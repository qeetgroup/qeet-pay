package com.qeetgroup.qeetpay.gst;

import java.util.List;

/**
 * The outcome of an {@link HsnClassifier#classify} call — the API/return contract for
 * {@code POST /v1/gst/classify} (PRD Module 05). Serialised into the classification cache and returned
 * verbatim on a cache hit, so it is a plain, Jackson-round-trippable record.
 *
 * @param decisionId the {@link com.qeetgroup.qeetpay.ai.AiGateway} audit-row id for this classification
 * @param model the model id that served the call (the offline sandbox stand-in unless a live client is wired)
 * @param source {@code "model"} when a real model's structured output was used, else {@code "deterministic"}
 * @param fellBack whether the gateway took the deterministic fail-closed path (model error/timeout/unusable)
 * @param requiresReview whether the primary suggestion is low-confidence and should be human-reviewed (§6.4)
 * @param confidence confidence in the primary suggestion, {@code [0,1]}
 * @param explanation a human-readable rationale for the primary suggestion
 * @param suggestions ranked HSN/SAC candidates, highest-confidence first
 */
public record ClassificationResult(
        String decisionId,
        String model,
        String source,
        boolean fellBack,
        boolean requiresReview,
        double confidence,
        String explanation,
        List<HsnSuggestion> suggestions) {}
