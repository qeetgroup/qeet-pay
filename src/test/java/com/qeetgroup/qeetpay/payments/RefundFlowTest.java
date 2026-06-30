package com.qeetgroup.qeetpay.payments;

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
 * Refund flow (TAD §4): a refund reverses the capture posting (debit revenue / credit settlement),
 * supports partials, and cannot exceed the captured amount or touch an un-captured payment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RefundFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired LedgerService ledger;

    @Test
    void partialThenFullRefundReversesTheLedger() {
        UUID merchantId = newMerchant();
        UUID paymentId = capturedPayment(merchantId, 499900);

        payments.refund(merchantId, paymentId, 200000, "partial");
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(299900);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(299900);

        Refund second = payments.refund(merchantId, paymentId, 299900, "remainder");
        assertThat(second.getStatus()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isZero();
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isZero();
    }

    @Test
    void refundCannotExceedCapturedAmount() {
        UUID merchantId = newMerchant();
        UUID paymentId = capturedPayment(merchantId, 100000);

        payments.refund(merchantId, paymentId, 100000, null);
        assertThatThrownBy(() -> payments.refund(merchantId, paymentId, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotRefundAnUncapturedPayment() {
        UUID merchantId = newMerchant();
        Payment authorizedOnly = payments.create(merchantId, 100000, "INR", PaymentMethod.UPI, "x", false);

        assertThatThrownBy(() -> payments.refund(merchantId, authorizedOnly.getId(), 100000, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID capturedPayment(UUID merchantId, long amountMinor) {
        Payment p = payments.create(merchantId, amountMinor, "INR", PaymentMethod.UPI, "sale", false);
        return payments.capture(merchantId, p.getId()).getId();
    }

    private UUID newMerchant() {
        return merchants.create("rf-" + UUID.randomUUID().toString().substring(0, 8), "Refund Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
