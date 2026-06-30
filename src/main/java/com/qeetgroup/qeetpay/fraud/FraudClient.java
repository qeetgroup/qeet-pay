package com.qeetgroup.qeetpay.fraud;

/** Scores a payment for fraud risk (TAD §8). Implementations must never throw on transport errors. */
public interface FraudClient {
    FraudDecision score(FraudCheck check);
}
