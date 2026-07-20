package com.qeetgroup.qeetpay.fraud;

import java.util.List;

/**
 * A fraud-scoring result: risk score 0–100, the verdict, the contributing signals, the explainable
 * top-contributing features ({@code topReasons}, TAD §8.4), and the scoring {@code model}
 * ({@code "onnx"} | {@code "rules"} | {@code null} when scoring is disabled).
 */
public record FraudDecision(
        int score,
        FraudDecisionType decision,
        List<String> reasons,
        List<FraudReason> topReasons,
        String model) {

    /** Backward-compatible constructor (no explanation / model) for the fail-open + fixture paths. */
    public FraudDecision(int score, FraudDecisionType decision, List<String> reasons) {
        this(score, decision, reasons, List.of(), null);
    }

    public static FraudDecision allow(String reason) {
        return new FraudDecision(0, FraudDecisionType.ALLOW, List.of(reason));
    }
}
