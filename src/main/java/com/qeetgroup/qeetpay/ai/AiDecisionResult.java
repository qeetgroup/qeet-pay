package com.qeetgroup.qeetpay.ai;

import java.util.UUID;

/**
 * The outcome of an {@link AiGateway#evaluate} call.
 *
 * @param decisionId the persisted {@link AiDecision} audit-row id
 * @param outputJson the returned decision — the model result, or the deterministic fallback when
 *     {@link #fellBack()} is true
 * @param confidence confidence in the returned decision (deterministic fallbacks record {@code 1.0})
 * @param humanReviewed whether a human had reviewed this recommendation (carried through from the request)
 * @param fellBack whether the deterministic path was taken (model error/timeout, stale/ambiguous/
 *     low-confidence result, or a money-affecting decision awaiting human review)
 * @param requiresHumanReview whether this money-affecting decision still needs a human before being applied
 */
public record AiDecisionResult(
        UUID decisionId,
        String outputJson,
        double confidence,
        boolean humanReviewed,
        boolean fellBack,
        boolean requiresHumanReview) {}
