package com.qeetgroup.qeetpay.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.payments.Payment;
import com.qeetgroup.qeetpay.payments.PaymentMethod;
import com.qeetgroup.qeetpay.payments.PaymentService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Settlement ingestion + reconciliation happy path (TAD §6.2): capturing payments moves funds into
 * the settlement holding account; ingesting the matching provider settlement report posts the net
 * to bank, expenses the provider fee, clears the holding account to zero, and reconciles clean.
 * Re-ingesting the same report is a no-op (idempotent by provider settlement id).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SettlementReconciliationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired SettlementService settlements;
    @Autowired ReconciliationService reconciliation;
    @Autowired LedgerService ledger;

    @Test
    void ingestPostsToLedgerAndReconcilesClean() {
        UUID merchantId = newMerchant();
        UUID p1 = capturedPayment(merchantId, 100000);
        UUID p2 = capturedPayment(merchantId, 50000);

        // Two captures accumulate ₹1500 in the settlement holding account (debit-normal asset).
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(150000);

        SettlementReport report =
                new SettlementReport(
                        "RAZORPAY",
                        "setl_001",
                        "INR",
                        Instant.now(),
                        149646L, // net control total = 150000 gross - 300 fee - 54 tax
                        List.of(
                                new SettlementReport.Line(p1, "rzp_p1", 100000, 200, 36),
                                new SettlementReport.Line(p2, "rzp_p2", 50000, 100, 18)));

        Settlement settlement = settlements.ingest(merchantId, report);

        assertThat(settlement.getGrossAmountMinor()).isEqualTo(150000);
        assertThat(settlement.getFeeAmountMinor()).isEqualTo(300);
        assertThat(settlement.getTaxAmountMinor()).isEqualTo(54);
        assertThat(settlement.getNetAmountMinor()).isEqualTo(149646);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.RECONCILED);
        assertThat(settlement.getLedgerEntryId()).isNotNull();

        // Net into bank, provider cut (fee + GST) expensed, holding account cleared to zero.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(149646);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "fees"))).isEqualTo(354);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isZero();

        Reconciliation recon = reconciliation.forSettlement(merchantId, settlement.getId()).orElseThrow();
        assertThat(recon.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(recon.getMatchedCount()).isEqualTo(2);
        assertThat(recon.getDiscrepancyCount()).isZero();
        assertThat(reconciliation.discrepanciesOf(merchantId, recon.getId())).isEmpty();
    }

    @Test
    void reIngestingTheSameReportIsIdempotent() {
        UUID merchantId = newMerchant();
        UUID p1 = capturedPayment(merchantId, 80000);

        SettlementReport report =
                new SettlementReport(
                        "RAZORPAY",
                        "setl_dup",
                        "INR",
                        Instant.now(),
                        null,
                        List.of(new SettlementReport.Line(p1, "rzp_p1", 80000, 0, 0)));

        Settlement first = settlements.ingest(merchantId, report);
        Settlement again = settlements.ingest(merchantId, report);

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(settlements.list(merchantId)).hasSize(1);
        // Bank credited exactly once — the replay never re-posts.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(80000);
    }

    private UUID capturedPayment(UUID merchantId, long amountMinor) {
        Payment p = payments.create(merchantId, amountMinor, "INR", PaymentMethod.UPI, "sale", false);
        payments.capture(merchantId, p.getId());
        return p.getId();
    }

    private UUID newMerchant() {
        return merchants
                .create("recon-" + UUID.randomUUID().toString().substring(0, 8), "Recon Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
