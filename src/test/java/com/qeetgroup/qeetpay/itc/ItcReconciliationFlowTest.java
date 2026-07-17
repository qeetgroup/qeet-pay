package com.qeetgroup.qeetpay.itc;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
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
 * ITC / GSTR-2B reconciliation flow (PRD Module 05/06): recording inward-supply purchase invoices,
 * reconciling them against supplier-filed 2B lines (MATCHED / MISMATCHED / MISSING_IN_2B), and summing
 * eligible ITC only over invoices that are both MATCHED and itc_eligible.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ItcReconciliationFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired ItcService itc;

    @Test
    void reconcilesAgainst2bAndSumsOnlyMatchedEligibleItc() {
        UUID merchantId = newMerchant();
        LocalDate today = LocalDate.now();

        // Invoice A: eligible, 18% GST on ₹1000 taxable => ₹180 total GST. Matches 2B exactly.
        PurchaseInvoice a =
                itc.recordPurchase(merchantId, "27AAAAA0000A1Z5", "Alpha Supplies", "INV-A", today,
                        100_000, 9_000, 9_000, 0, true);
        // Invoice B: eligible, but its 2B line will report a different total GST => MISMATCHED.
        PurchaseInvoice b =
                itc.recordPurchase(merchantId, "27BBBBB0000B1Z5", "Beta Traders", "INV-B", today,
                        200_000, 18_000, 18_000, 0, true);
        // Invoice C: eligible, but no 2B line filed by the supplier => MISSING_IN_2B.
        PurchaseInvoice c =
                itc.recordPurchase(merchantId, "27CCCCC0000C1Z5", "Gamma Corp", "INV-C", today,
                        50_000, 4_500, 4_500, 0, true);
        // Invoice D: MATCHED against 2B but itc_eligible = false => excluded from the eligible sum.
        PurchaseInvoice d =
                itc.recordPurchase(merchantId, "27DDDDD0000D1Z5", "Delta Ltd", "INV-D", today,
                        30_000, 2_700, 2_700, 0, false);

        // Supplier-filed 2B: A exact (18_000), B differs (99_999), D exact (5_400); nothing for C.
        ItcService.ReconSummary summary =
                itc.reconcileAgainst2b(
                        merchantId,
                        List.of(
                                new Gstr2bLine("27AAAAA0000A1Z5", "INV-A", 18_000),
                                new Gstr2bLine("27BBBBB0000B1Z5", "INV-B", 99_999),
                                new Gstr2bLine("27DDDDD0000D1Z5", "INV-D", 5_400)));

        assertThat(summary.matched()).isEqualTo(2); // A and D
        assertThat(summary.mismatched()).isEqualTo(1); // B
        assertThat(summary.missingIn2b()).isEqualTo(1); // C

        assertThat(itc.getPurchase(merchantId, a.getId()).getReconStatus()).isEqualTo(ReconStatus.MATCHED);
        assertThat(itc.getPurchase(merchantId, b.getId()).getReconStatus()).isEqualTo(ReconStatus.MISMATCHED);
        assertThat(itc.getPurchase(merchantId, c.getId()).getReconStatus())
                .isEqualTo(ReconStatus.MISSING_IN_2B);
        assertThat(itc.getPurchase(merchantId, d.getId()).getReconStatus()).isEqualTo(ReconStatus.MATCHED);
        assertThat(itc.getPurchase(merchantId, a.getId()).getReconciledAt()).isNotNull();

        // Only invoice A is both MATCHED and itc_eligible; D is MATCHED but ineligible, B/C not MATCHED.
        ItcService.EligibleItcSummary eligible = itc.eligibleItcSummary(merchantId);
        assertThat(eligible.eligibleInvoiceCount()).isEqualTo(1);
        assertThat(eligible.eligibleItcMinor()).isEqualTo(18_000); // A's total GST only
    }

    @Test
    void listsPurchasesAndDerivesTotalGst() {
        UUID merchantId = newMerchant();
        PurchaseInvoice invoice =
                itc.recordPurchase(merchantId, "27AAAAA0000A1Z5", "Alpha Supplies", "INV-1",
                        LocalDate.now(), 100_000, 9_000, 9_000, 0, true);

        assertThat(invoice.getTotalGstMinor()).isEqualTo(18_000); // cgst + sgst + igst
        assertThat(invoice.getReconStatus()).isEqualTo(ReconStatus.UNMATCHED);
        assertThat(itc.listPurchases(merchantId)).hasSize(1);
    }

    private UUID newMerchant() {
        return merchants.create("itc-" + UUID.randomUUID().toString().substring(0, 8), "ITC Co")
                .merchant().getId();
    }
}
