package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * GST invoice flow (TAD §7.2): an intra-state invoice computes CGST+SGST and gets a number; paying it
 * posts the 3-line entry so revenue == taxable, tax_payable == total GST, settlement == grand total.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GstInvoiceFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired GstInvoiceService gst;
    @Autowired LedgerService ledger;

    @Test
    void intraStateInvoicePaysIntoRevenueAndTaxPayable() {
        UUID merchantId = newMerchant();

        // supplier in state 27 (Maharashtra), place of supply 27 => intra-state.
        GstInvoiceService.InvoiceWithLines created =
                gst.createInvoice(
                        merchantId,
                        "27ABCDE1234F1Z5",
                        "27AAAAA0000A1Z5",
                        "27",
                        "INR",
                        List.of(
                                new GstLineInput("Pro plan", "998314", 1, 100_000, 18),
                                new GstLineInput("Add-on seats", "998314", 2, 50_000, 18)));

        GstInvoice inv = created.invoice();
        assertThat(inv.getStatus()).isEqualTo(GstInvoiceStatus.ISSUED);
        assertThat(inv.getInvoiceNumber()).contains("/" + GstInvoiceService.fiscalYear(java.time.Instant.now()) + "/");
        assertThat(inv.getSupplyType()).isEqualTo(SupplyType.INTRA_STATE);
        assertThat(inv.getTaxableMinor()).isEqualTo(200_000);
        assertThat(inv.getCgstMinor()).isEqualTo(18_000);
        assertThat(inv.getSgstMinor()).isEqualTo(18_000);
        assertThat(inv.getTotalGstMinor()).isEqualTo(36_000);
        assertThat(inv.getTotalMinor()).isEqualTo(236_000);

        GstInvoice paid = gst.payInvoice(merchantId, inv.getId());
        assertThat(paid.getStatus()).isEqualTo(GstInvoiceStatus.PAID);
        assertThat(paid.getLedgerEntryId()).isNotNull();

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(200_000);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "tax_payable"))).isEqualTo(36_000);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(236_000);
    }

    @Test
    void payingInvoiceIsIdempotent() {
        UUID merchantId = newMerchant();
        var inv =
                gst.createInvoice(
                                merchantId, "27ABCDE1234F1Z5", null, "27", "INR",
                                List.of(new GstLineInput("Svc", "998314", 1, 100_000, 18)))
                        .invoice();

        gst.payInvoice(merchantId, inv.getId());
        gst.payInvoice(merchantId, inv.getId()); // no-op replay

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(100_000);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "tax_payable"))).isEqualTo(18_000);
    }

    private UUID newMerchant() {
        return merchants.create("gst-" + UUID.randomUUID().toString().substring(0, 8), "GST Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
