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

/** Payment acceptance flow (TAD §4.1): authorize on create, post to the ledger on capture. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired LedgerService ledger;

    @Test
    void capturedPaymentPostsBalancedLedgerEntry() {
        UUID merchantId = newMerchant();

        Payment created = payments.create(merchantId, 499900, "INR", PaymentMethod.UPI, "sale", false);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

        Payment captured = payments.capture(merchantId, created.getId());
        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.getLedgerEntryId()).isNotNull();

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(499900);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(499900);
    }

    @Test
    void captureIsIdempotentAndNeverDoublePosts() {
        UUID merchantId = newMerchant();
        Payment p = payments.create(merchantId, 100000, "INR", PaymentMethod.CARD, "sale", false);

        payments.capture(merchantId, p.getId());
        payments.capture(merchantId, p.getId()); // no-op replay

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(100000);
    }

    @Test
    void listReturnsOnlyTheMerchantsOwnPaymentsNewestFirst() {
        UUID merchantA = newMerchant();
        UUID merchantB = newMerchant();

        payments.create(merchantA, 10000, "INR", PaymentMethod.UPI, "first", false);
        Payment second = payments.create(merchantA, 20000, "INR", PaymentMethod.CARD, "second", false);
        payments.create(merchantB, 30000, "INR", PaymentMethod.UPI, "other", false);

        var listA = payments.list(merchantA);
        assertThat(listA).hasSize(2);
        assertThat(listA.get(0).getId()).isEqualTo(second.getId()); // newest first
        assertThat(listA).noneMatch(p -> p.getMerchantId().equals(merchantB));

        assertThat(payments.list(merchantB)).hasSize(1);
    }

    @Test
    void failedAuthorizationCannotBeCaptured() {
        UUID merchantId = newMerchant();
        Payment p = payments.create(merchantId, 100000, "INR", PaymentMethod.UPI, "bad", true);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThatThrownBy(() -> payments.capture(merchantId, p.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("pay-" + UUID.randomUUID().toString().substring(0, 8), "Pay Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
