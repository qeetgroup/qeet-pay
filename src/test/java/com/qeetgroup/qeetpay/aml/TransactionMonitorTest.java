package com.qeetgroup.qeetpay.aml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure transaction-monitoring rules: structuring, velocity, geo anomaly, high-risk MCC. */
class TransactionMonitorTest {

    @Test
    void flagsStructuringJustBelowTheCtrThreshold() {
        // ₹9,50,000 — sits in [90%, 100%) of the ₹10,00,000 CTR threshold.
        List<RuleHit> hits =
                TransactionMonitor.evaluate(
                        new TransactionSignal("txn_1", 95_000_000L, "INR", null, null, null, null, null));
        assertThat(hits).extracting(RuleHit::ruleCode).containsExactly("AML-STRUCT-01");
        assertThat(hits.get(0).category()).isEqualTo("STRUCTURING");
        assertThat(hits.get(0).riskScore()).isEqualTo(70);
    }

    @Test
    void aCleanTransactionFiresNoRules() {
        List<RuleHit> hits =
                TransactionMonitor.evaluate(
                        new TransactionSignal("txn_2", 50_000_00L, "INR", 5411, "IN", 3, 20_000_00L, null));
        assertThat(hits).isEmpty();
    }

    @Test
    void flagsVelocityOnHighTrailingCount() {
        List<RuleHit> hits =
                TransactionMonitor.evaluate(
                        new TransactionSignal("txn_3", 10_000_00L, "INR", null, "IN", 120, null, null));
        assertThat(hits).extracting(RuleHit::ruleCode).contains("AML-VELO-01");
    }

    @Test
    void flagsGeoAnomalyForHighRiskJurisdiction() {
        List<RuleHit> hits =
                TransactionMonitor.evaluate(
                        new TransactionSignal("txn_4", 10_000_00L, "INR", null, "KP", null, null, null));
        assertThat(hits).extracting(RuleHit::ruleCode).contains("AML-GEO-01");
    }

    @Test
    void flagsHighRiskMcc() {
        List<RuleHit> hits =
                TransactionMonitor.evaluate(
                        new TransactionSignal("txn_5", 10_000_00L, "INR", 7995, "IN", null, null, null));
        assertThat(hits).extracting(RuleHit::ruleCode).contains("AML-MCC-01");
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(
                        () ->
                                TransactionMonitor.evaluate(
                                        new TransactionSignal("txn_6", 0, "INR", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
