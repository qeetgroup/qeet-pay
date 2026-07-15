package com.qeetgroup.qeetpay.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * Cross-border flow (PRD Module 14): a USD export invoice is settled by a foreign inward remittance,
 * FX-converted to INR at ₹83.50/USD, captured with a FIRA reference and posted to the ledger as
 * money-in; the invoice becomes REMITTED and cannot be double-settled.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CrossBorderFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired CrossBorderService crossBorder;
    @Autowired LedgerService ledger;

    @Test
    void exportInvoiceRemittanceConvertsToInrAndPosts() {
        UUID merchantId = newMerchant();

        ExportInvoice invoice =
                crossBorder.createExportInvoice(
                        merchantId, "EXP/2026/001", "US", "USD", 100_000L, "P0802", true);
        assertThat(invoice.getStatus()).isEqualTo(ExportInvoiceStatus.ISSUED);
        assertThat(invoice.isLut()).isTrue();

        InwardRemittance r = crossBorder.recordRemittance(merchantId, invoice.getId(), 100_000L, "FIRA-2026-001");
        assertThat(r.getInrAmountMinor()).isEqualTo(8_350_000L); // 100,000 cents × 83.50
        assertThat(r.getFiraReference()).isEqualTo("FIRA-2026-001");
        assertThat(r.getPurposeCode()).isEqualTo("P0802");

        // INR money-in posted to the ledger.
        assertThat(balance(merchantId, "settlement")).isEqualTo(8_350_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(8_350_000L);

        CrossBorderService.InvoiceWithRemittances after = crossBorder.getExportInvoice(merchantId, invoice.getId());
        assertThat(after.invoice().getStatus()).isEqualTo(ExportInvoiceStatus.REMITTED);
        assertThat(after.remittances()).hasSize(1);
    }

    @Test
    void cannotRemitTwice() {
        UUID merchantId = newMerchant();
        ExportInvoice invoice =
                crossBorder.createExportInvoice(merchantId, "EXP/2026/002", "GB", "GBP", 50_000L, "P0802", true);
        crossBorder.recordRemittance(merchantId, invoice.getId(), 50_000L, "FIRA-A");

        assertThatThrownBy(() -> crossBorder.recordRemittance(merchantId, invoice.getId(), 50_000L, "FIRA-B"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInrExportCurrency() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() ->
                        crossBorder.createExportInvoice(merchantId, "EXP/X", "US", "INR", 1000L, "P0802", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID newMerchant() {
        return merchants.create("cb-" + UUID.randomUUID().toString().substring(0, 8), "Export Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
