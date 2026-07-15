package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * E-invoicing flow (TAD §7.3): registering an issued invoice yields a 64-char IRN + signed QR via the
 * sandbox IRP adapter; re-registering is idempotent; and an IRN can be cancelled with a reason.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EInvoiceFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired GstInvoiceService gst;
    @Autowired EInvoiceService eInvoice;

    @Test
    void generatesIrnAndSignedQr() {
        UUID merchantId = newMerchant();
        UUID invoiceId = newInvoice(merchantId);

        GstInvoice registered = eInvoice.generateIrn(merchantId, invoiceId);

        assertThat(registered.getIrnStatus()).isEqualTo(IrnStatus.GENERATED);
        assertThat(registered.getIrn()).hasSize(64);
        assertThat(registered.getIrpAckNo()).isNotBlank();
        assertThat(registered.getIrpAckDate()).isNotNull();
        assertThat(registered.getSignedQrCode()).isNotBlank();
        assertThat(registered.getIrnGeneratedAt()).isNotNull();
    }

    @Test
    void generatingIsIdempotent() {
        UUID merchantId = newMerchant();
        UUID invoiceId = newInvoice(merchantId);

        String first = eInvoice.generateIrn(merchantId, invoiceId).getIrn();
        String second = eInvoice.generateIrn(merchantId, invoiceId).getIrn();

        assertThat(second).isEqualTo(first); // same IRN, not a fresh registration
    }

    @Test
    void cancelsIrnWithReason() {
        UUID merchantId = newMerchant();
        UUID invoiceId = newInvoice(merchantId);
        eInvoice.generateIrn(merchantId, invoiceId);

        GstInvoice cancelled = eInvoice.cancelIrn(merchantId, invoiceId, "1"); // 1 = duplicate

        assertThat(cancelled.getIrnStatus()).isEqualTo(IrnStatus.CANCELLED);
        assertThat(cancelled.getIrnCancelReason()).isEqualTo("1");
        assertThat(cancelled.getIrnCancelledAt()).isNotNull();
        assertThat(cancelled.getIrn()).hasSize(64); // number retained for audit
    }

    @Test
    void cannotCancelWithoutActiveIrn() {
        UUID merchantId = newMerchant();
        UUID invoiceId = newInvoice(merchantId);

        assertThatThrownBy(() -> eInvoice.cancelIrn(merchantId, invoiceId, "1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("ein-" + UUID.randomUUID().toString().substring(0, 8), "EInvoice Co")
                .merchant().getId();
    }

    private UUID newInvoice(UUID merchantId) {
        return gst.createInvoice(
                        merchantId, "27ABCDE1234F1Z5", "27AAAAA0000A1Z5", "27", "INR",
                        List.of(new GstLineInput("Pro plan", "998314", 1, 100_000, 18)))
                .invoice()
                .getId();
    }
}
