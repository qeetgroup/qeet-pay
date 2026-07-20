package com.qeetgroup.qeetpay.payments;

/**
 * One provider's position in an {@link AiProviderScorer} ranking (PRD Module 07.3). Carries the inputs
 * the score was computed from (predicted auth-rate, cost, health) plus the resulting score and a
 * plain-English {@code why}, so the routing decision is explainable.
 *
 * @param provider the acquirer name (e.g. {@code "RAZORPAY"})
 * @param predictedAuthRate predicted authorization rate in {@code [0,1]}
 * @param costBps the provider's cost basis in basis points
 * @param health the provider's rolling health signal ({@link ProviderHealth})
 * @param score the ranking score (higher is better; a DOWN provider scores {@code -Infinity})
 * @param why a plain-English explanation of this provider's score
 */
public record RankedProvider(
        String provider,
        double predictedAuthRate,
        int costBps,
        String health,
        double score,
        String why) {}
