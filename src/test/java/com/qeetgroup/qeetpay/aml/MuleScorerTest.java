package com.qeetgroup.qeetpay.aml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure mule-account heuristic: rapid pass-through + fan-in/out drive the score and the flag. */
class MuleScorerTest {

    @Test
    void flagsRapidPassThroughFanInAccount() {
        // ₹5,00,000 in from 30 sources, ₹4,90,000 straight back out within 5 minutes.
        MuleAssessment a =
                MuleScorer.score(
                        new BeneficiaryActivity("ben_mule", 50_000_000L, 49_000_000L, 30, 12, 300, 40));
        assertThat(a.flagged()).isTrue();
        assertThat(a.riskScore()).isGreaterThanOrEqualTo(MuleScorer.ALERT_THRESHOLD);
        assertThat(a.reasons()).isNotEmpty();
    }

    @Test
    void doesNotFlagOrdinaryBeneficiary() {
        // Money held, only a couple of counterparties, no pass-through.
        MuleAssessment a =
                MuleScorer.score(
                        new BeneficiaryActivity("ben_ok", 50_000_000L, 1_000_000L, 2, 1, 86_400, 2));
        assertThat(a.flagged()).isFalse();
        assertThat(a.severity()).isEqualTo(AlertSeverity.LOW);
    }
}
