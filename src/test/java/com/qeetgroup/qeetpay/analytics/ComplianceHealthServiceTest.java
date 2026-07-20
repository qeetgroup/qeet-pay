package com.qeetgroup.qeetpay.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.kyb.KybService;
import com.qeetgroup.qeetpay.kyb.MerchantKyb;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.reconciliation.SettlementReport;
import com.qeetgroup.qeetpay.reconciliation.SettlementService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Unified Financial-Health &amp; Compliance Dashboard (PRD Module 12.6): asserts the composite shape
 * over seeded data across four domains — settlement/nodal reconciliation, GSTR filing, fraud posture,
 * KYB/onboarding — plus headline financial KPIs. Read-only composition; no ledger writes here beyond
 * seeding fixtures. Uses the shared singleton-container base ({@link AbstractIntegrationTest}).
 */
class ComplianceHealthServiceTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired AnalyticsIngestor ingestor;
    @Autowired KybService kyb;
    @Autowired LedgerService ledger;
    @Autowired SettlementService settlements;
    @Autowired ComplianceHealthService complianceHealth;

    @Test
    void composesHealthyDashboardAcrossAllDomains() {
        UUID merchantId = newMerchant();

        // KYB: sandbox adapter verifies well-formed values → overall VERIFIED (onboarding complete).
        kyb.submitPan(merchantId, "ABCDE1234F");
        kyb.submitGstin(merchantId, "27ABCDE1234F1Z5");
        kyb.submitBank(merchantId, "000111222333", "HDFC0001234");

        // Nodal: seed a positive settlement holding balance (debit settlement / credit revenue).
        seedSettlementBalance(merchantId, 500_000L);

        // Financial KPIs: ₹3,000 captured TPV (2 txns) + 1 failed; ₹1,000 MRR from a NEW subscription.
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 200_000L, "UPI");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 100_000L, "CARD");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.FAILED, 50_000L, "UPI");
        ingestor.recordSubscriptionEvent(merchantId, UUID.randomUUID(), SubscriptionAnalyticsEvent.NEW, 100_000L);

        ComplianceHealthService.ComplianceHealth health = complianceHealth.compose(merchantId, 30);

        // Envelope
        assertThat(health.merchantId()).isEqualTo(merchantId);
        assertThat(health.generatedAt()).isNotNull();
        assertThat(health.overallStatus()).isEqualTo(ComplianceHealthService.HEALTHY);

        // Reconciliation / nodal health
        ComplianceHealthService.ReconHealth recon = health.reconciliation();
        assertThat(recon.totalSettlements()).isZero();
        assertThat(recon.discrepancyCount()).isZero();
        assertThat(recon.nodalBalanceMinor()).isEqualTo(500_000L);
        assertThat(recon.nodalHealthy()).isTrue();
        assertThat(recon.status()).isEqualTo(ComplianceHealthService.HEALTHY);
        assertThat(recon.asOf()).isNotNull();

        // Filing (nothing prepared/filed → healthy, empty)
        ComplianceHealthService.FilingSummary filing = health.filing();
        assertThat(filing.totalReturns()).isZero();
        assertThat(filing.errorCount()).isZero();
        assertThat(filing.latestFiledPeriod()).isNull();
        assertThat(filing.status()).isEqualTo(ComplianceHealthService.HEALTHY);

        // Fraud posture (test profile → scoring disabled, advisory)
        ComplianceHealthService.FraudPosture fraud = health.fraud();
        assertThat(fraud.scoringEnabled()).isFalse();
        assertThat(fraud.mode()).isEqualTo("ADVISORY_DISABLED");
        assertThat(fraud.description()).contains("advisory");

        // KYB / onboarding
        ComplianceHealthService.KybOnboarding onboarding = health.kyb();
        assertThat(onboarding.overallStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(onboarding.panStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(onboarding.gstinStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(onboarding.bankStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(onboarding.onboardingComplete()).isTrue();
        assertThat(onboarding.verifiedAt()).isNotNull();

        // Headline financial KPIs (paise)
        ComplianceHealthService.FinancialKpis kpis = health.kpis();
        assertThat(kpis.currentMrrMinor()).isEqualTo(100_000L);
        assertThat(kpis.currentArrMinor()).isEqualTo(1_200_000L);
        assertThat(kpis.trailingTpvMinor()).isEqualTo(300_000L);
        assertThat(kpis.trailingSuccessRatePercent()).isCloseTo(66.67, Offset.offset(0.1));
        assertThat(kpis.windowDays()).isEqualTo(30);
    }

    @Test
    void flagsAttentionWhenSettlementDiscrepancyAndNodalNegative() {
        UUID merchantId = newMerchant();

        // Ingest a settlement whose only line references a payment that was never captured. Reconciliation
        // flags it (MISSING_IN_LEDGER) and the settlement posting drives the nodal account negative
        // (credit settlement gross with no offsetting capture) → NODAL_IMBALANCE → status DISCREPANCY.
        SettlementReport report =
                new SettlementReport(
                        "razorpay",
                        "setl_" + UUID.randomUUID(),
                        "INR",
                        Instant.now(),
                        null,
                        List.of(new SettlementReport.Line(UUID.randomUUID(), "pay_unmatched", 100_000L, 2_000L, 360L)));
        settlements.ingest(merchantId, report);

        ComplianceHealthService.ComplianceHealth health = complianceHealth.compose(merchantId, 30);

        ComplianceHealthService.ReconHealth recon = health.reconciliation();
        assertThat(recon.totalSettlements()).isEqualTo(1);
        assertThat(recon.discrepancyCount()).isEqualTo(1);
        assertThat(recon.nodalBalanceMinor()).isNegative();
        assertThat(recon.nodalHealthy()).isFalse();
        assertThat(recon.status()).isEqualTo(ComplianceHealthService.ATTENTION);

        // Any single unhealthy domain drives the composite to ATTENTION.
        assertThat(health.overallStatus()).isEqualTo(ComplianceHealthService.ATTENTION);
    }

    @Test
    void rejectsOutOfRangeWindow() {
        UUID merchantId = newMerchant();
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> complianceHealth.compose(merchantId, 0));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> complianceHealth.compose(merchantId, 366));
    }

    private UUID newMerchant() {
        return merchants
                .create("ch-" + UUID.randomUUID().toString().substring(0, 8), "Compliance Health Co")
                .merchant()
                .getId();
    }

    private void seedSettlementBalance(UUID merchantId, long amountMinor) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        ledger.postEntry(
                merchantId,
                "seed",
                "INR",
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                        new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));
    }
}
