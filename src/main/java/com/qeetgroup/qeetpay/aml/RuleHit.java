package com.qeetgroup.qeetpay.aml;

/**
 * A single rule firing from the transaction monitor: the rule code (e.g. {@code AML-STRUCT-01}), the
 * category, a 0–100 risk score, and a human-readable explanation.
 */
public record RuleHit(String ruleCode, String category, int riskScore, String detail) {}
