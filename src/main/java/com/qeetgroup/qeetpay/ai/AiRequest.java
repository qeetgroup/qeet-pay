package com.qeetgroup.qeetpay.ai;

import java.util.Set;
import java.util.UUID;

/**
 * A single request to {@link AiGateway#evaluate}. This is the contract every future AI feature fills
 * in — the gateway masks {@link #input()}, enforces the §6.4 safety matrix, and records the outcome.
 *
 * @param merchantId the tenant the decision is scoped to (RLS)
 * @param feature the feature key, e.g. {@code "gst.classification"} (see {@link AiFeature})
 * @param model the model id to pin, or {@code null} to use the client's default
 * @param input the raw prompt/input — may contain PII; the gateway masks it before any model call
 * @param moneyAffecting whether this decision moves/withholds money (also inferred for well-known
 *     features via {@link AiFeature#isMoneyAffecting}); money-affecting decisions require human review
 * @param humanReviewed whether a human has already reviewed/approved this recommendation (§6.4)
 * @param scopes the caller's RBAC scopes, passed through for permission-aware inference
 * @param minConfidence the confidence floor below which the gateway fails closed to the fallback
 */
public record AiRequest(
        UUID merchantId,
        String feature,
        String model,
        String input,
        boolean moneyAffecting,
        boolean humanReviewed,
        Set<String> scopes,
        double minConfidence) {

    /** Advisory (non-money-affecting) query with a default 0.5 confidence floor and no pinned model. */
    public static AiRequest advisory(UUID merchantId, String feature, String input, Set<String> scopes) {
        return new AiRequest(merchantId, feature, null, input, false, false, scopes, 0.5);
    }
}
