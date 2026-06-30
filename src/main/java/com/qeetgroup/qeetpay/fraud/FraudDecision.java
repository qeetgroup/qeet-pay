package com.qeetgroup.qeetpay.fraud;

import java.util.List;

/** A fraud-scoring result: risk score 0–100, the verdict, and the contributing signals. */
public record FraudDecision(int score, FraudDecisionType decision, List<String> reasons) {

    public static FraudDecision allow(String reason) {
        return new FraudDecision(0, FraudDecisionType.ALLOW, List.of(reason));
    }
}
