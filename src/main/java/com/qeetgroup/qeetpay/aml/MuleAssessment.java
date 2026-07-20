package com.qeetgroup.qeetpay.aml;

import java.util.List;

/**
 * The result of scoring a beneficiary for mule-account behaviour: a 0–100 risk score, its severity
 * band, whether it crossed the alert threshold, and the reasons that contributed to the score.
 */
public record MuleAssessment(
        String beneficiaryRef,
        int riskScore,
        AlertSeverity severity,
        boolean flagged,
        List<String> reasons) {}
