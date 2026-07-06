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
 * Reconciliation catches the ways a provider settlement report can diverge from the ledger (TAD
 * §6.2): a mismatched amount, a payment we never saw, one that wasn't captured, a double-settled
 * payment, a bad batch control total, and the nodal invariant (never settle out more than held).
 * A discrepancy flags the settlement for review — it never blocks the posting.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ReconciliationDiscrepancyTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired SettlementService settlements;
    @Autowired ReconciliationService reconciliation;

    @Test
    void flagsAmountMismatch() {
        UUID merchantId = newMerchant();
        UUID p1 = capturedPayment(merchantId, 100000); // captured ₹1000

        Settlement s =
                settlements.ingest(
                        merchantId,
                        report("setl_am", null, line(p1, 90000))); // report says ₹900

        assertThat(s.getStatus()).isEqualTo(SettlementStatus.DISCREPANCY);
        Reconciliation recon = reconciliation.forSettlement(merchantId, s.getId()).orElseThrow();
        assertThat(recon.getMatchedCount()).isZero();
        assertThat(types(merchantId, recon)).contains(DiscrepancyType.AMOUNT_MISMATCH);
    }

    @Test
    void flagsPaymentMissingFromLedger() {
        UUID merchantId = newMerchant();
        capturedPayment(merchantId, 100000); // holding backstop so this isn't also a nodal breach
        UUID ghost = UUID.randomUUID();

        Settlement s = settlements.ingest(merchantId, report("setl_miss", null, line(ghost, 40000)));

        Reconciliation recon = reconciliation.forSettlement(merchantId, s.getId()).orElseThrow();
        assertThat(types(merchantId, recon)).contains(DiscrepancyType.MISSING_IN_LEDGER);
    }

    @Test
    void flagsPaymentNotYetCaptured() {
        UUID merchantId = newMerchant();
        capturedPayment(merchantId, 100000); // holding backstop
        Payment authorizedOnly = payments.create(merchantId, 50000, "INR", PaymentMethod.CARD, "auth", false);

        Settlement s =
                settlements.ingest(
                        merchantId, report("setl_status", null, line(authorizedOnly.getId(), 50000)));

        Reconciliation recon = reconciliation.forSettlement(merchantId, s.getId()).orElseThrow();
        assertThat(recon.getMatchedCount()).isZero();
        assertThat(types(merchantId, recon)).contains(DiscrepancyType.STATUS_NOT_CAPTURED);
    }

    @Test
    void flagsDoubleSettlementAndNodalBreach() {
        UUID merchantId = newMerchant();
        UUID p1 = capturedPayment(merchantId, 100000);

        Settlement first = settlements.ingest(merchantId, report("setl_1", null, line(p1, 100000)));
        assertThat(first.getStatus()).isEqualTo(SettlementStatus.RECONCILED); // holding now zero

        // Settling p1 a second time both duplicates it and drives the holding account negative.
        Settlement second = settlements.ingest(merchantId, report("setl_2", null, line(p1, 100000)));
        assertThat(second.getStatus()).isEqualTo(SettlementStatus.DISCREPANCY);
        assertThat(types(merchantId, reconciliation.forSettlement(merchantId, second.getId()).orElseThrow()))
                .contains(DiscrepancyType.DUPLICATE_SETTLEMENT, DiscrepancyType.NODAL_IMBALANCE);
    }

    @Test
    void flagsBatchControlTotalMismatch() {
        UUID merchantId = newMerchant();
        UUID p1 = capturedPayment(merchantId, 100000);

        // Lines net to 100000, but the report claims 99999 — the batch total doesn't tie out.
        Settlement s = settlements.ingest(merchantId, report("setl_bt", 99999L, line(p1, 100000)));

        Reconciliation recon = reconciliation.forSettlement(merchantId, s.getId()).orElseThrow();
        assertThat(recon.getMatchedCount()).isEqualTo(1); // the line itself is clean
        assertThat(types(merchantId, recon)).containsExactly(DiscrepancyType.BATCH_TOTAL_MISMATCH);
    }

    private SettlementReport report(String settlementId, Long reportedNetMinor, SettlementReport.Line line) {
        return new SettlementReport("RAZORPAY", settlementId, "INR", Instant.now(), reportedNetMinor, List.of(line));
    }

    private SettlementReport.Line line(UUID paymentId, long grossMinor) {
        return new SettlementReport.Line(paymentId, "rzp_ref", grossMinor, 0, 0);
    }

    private List<DiscrepancyType> types(UUID merchantId, Reconciliation recon) {
        return reconciliation.discrepanciesOf(merchantId, recon.getId()).stream()
                .map(Discrepancy::getType)
                .toList();
    }

    private UUID capturedPayment(UUID merchantId, long amountMinor) {
        Payment p = payments.create(merchantId, amountMinor, "INR", PaymentMethod.UPI, "sale", false);
        payments.capture(merchantId, p.getId());
        return p.getId();
    }

    private UUID newMerchant() {
        return merchants
                .create("recd-" + UUID.randomUUID().toString().substring(0, 8), "Recon Disc Co")
                .merchant()
                .getId();
    }
}
