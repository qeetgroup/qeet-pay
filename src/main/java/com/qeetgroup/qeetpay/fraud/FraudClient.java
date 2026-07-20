package com.qeetgroup.qeetpay.fraud;

/**
 * Scores a payment for fraud risk (TAD §8). The bean payments consult during authorization; the
 * production implementation ({@link AiGatewayFraudClient}) routes the underlying {@link FraudScorer}
 * through the {@code ai/AiGateway} §6.4 safety substrate (PII masking, audit + outbox, fail-closed to
 * the deterministic rule path) and persists a {@link FraudDecisionRecord}. Implementations must never
 * throw on transport errors.
 */
public interface FraudClient {
    FraudDecision score(FraudCheck check);
}
