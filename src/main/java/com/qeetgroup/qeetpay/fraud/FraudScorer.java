package com.qeetgroup.qeetpay.fraud;

/**
 * Low-level fraud scorer — the "deterministic path" behind {@link FraudClient}. Implementations are
 * the Python fraud-svc HTTP client ({@link HttpFraudClient}) or the allow-all fallback
 * ({@link FraudConfig}). They must never throw on transport errors (fail open — scoring is advisory).
 *
 * <p>{@link AiGatewayFraudClient} wraps the active {@code FraudScorer} so every score is masked,
 * audited and persisted through the {@code ai/AiGateway} §6.4 safety substrate.
 */
public interface FraudScorer {
    FraudDecision score(FraudCheck check);
}
