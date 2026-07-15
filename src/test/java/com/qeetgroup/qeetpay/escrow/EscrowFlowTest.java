package com.qeetgroup.qeetpay.escrow;

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
 * Digital-escrow flow (PRD Module 10): holding moves cash into escrow_payable; a partial release to
 * the seller and a partial refund to the buyer fully allocate it (status SETTLED), and escrow_payable
 * nets to zero. Over-allocation is rejected.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EscrowFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired EscrowService escrow;
    @Autowired LedgerService ledger;

    @Test
    void holdThenPartialReleaseAndRefundSettles() {
        UUID merchantId = newMerchant();

        EscrowService.AgreementWithEvents held =
                escrow.hold(merchantId, "buyer-1", "seller-1", 100_000L, "INR", "Order #1 delivery");
        UUID escrowId = held.agreement().getId();
        assertThat(held.agreement().getStatus()).isEqualTo(EscrowStatus.HELD);
        // Funds held: settlement debited, escrow_payable credited.
        assertThat(balance(merchantId, "settlement")).isEqualTo(100_000L);
        assertThat(balance(merchantId, "escrow_payable")).isEqualTo(100_000L);

        // Release ₹700 to the seller.
        escrow.release(merchantId, escrowId, 70_000L, "delivery confirmed (partial)");
        assertThat(balance(merchantId, "escrow_payable")).isEqualTo(30_000L);
        assertThat(balance(merchantId, "liability")).isEqualTo(70_000L); // seller payable
        assertThat(escrow.getAgreement(merchantId, escrowId).agreement().getStatus()).isEqualTo(EscrowStatus.HELD);

        // Refund the remaining ₹300 to the buyer → fully allocated (SETTLED).
        EscrowAgreement settled = escrow.refund(merchantId, escrowId, 30_000L, "partial cancellation");
        assertThat(settled.getStatus()).isEqualTo(EscrowStatus.SETTLED);
        assertThat(settled.remainingMinor()).isZero();
        assertThat(balance(merchantId, "escrow_payable")).isZero();
        // settlement retained = held − refunded = 100,000 − 30,000.
        assertThat(balance(merchantId, "settlement")).isEqualTo(70_000L);
        assertThat(escrow.getAgreement(merchantId, escrowId).events()).hasSize(3); // HOLD + RELEASE + REFUND
    }

    @Test
    void fullReleaseMarksReleased() {
        UUID merchantId = newMerchant();
        UUID escrowId = escrow.hold(merchantId, "b", "s", 50_000L, "INR", null).agreement().getId();
        EscrowAgreement released = escrow.release(merchantId, escrowId, 50_000L, "delivered");
        assertThat(released.getStatus()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(balance(merchantId, "escrow_payable")).isZero();
    }

    @Test
    void cannotOverAllocate() {
        UUID merchantId = newMerchant();
        UUID escrowId = escrow.hold(merchantId, "b", "s", 50_000L, "INR", null).agreement().getId();
        escrow.release(merchantId, escrowId, 40_000L, null);
        assertThatThrownBy(() -> escrow.refund(merchantId, escrowId, 20_000L, null)) // only 10,000 left
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID newMerchant() {
        return merchants.create("esc-" + UUID.randomUUID().toString().substring(0, 8), "Escrow Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
