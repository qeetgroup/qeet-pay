package com.qeetgroup.qeetpay.paymentlinks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.payments.PaymentMethod;
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
 * Payment-link flow (PRD Module 01): paying a fixed-amount link drives a real payment (captured to the
 * ledger) and marks the link PAID; open-amount links take the amount at pay time; a cancelled link
 * can't be paid.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentLinkFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentLinkService paymentLinks;
    @Autowired LedgerService ledger;

    @Test
    void fixedAmountLinkPaymentCapturesToLedger() {
        UUID merchantId = newMerchant();
        PaymentLink link = paymentLinks.createLink(merchantId, "Invoice #42", 150_000L, "INR", "order-42", null);
        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE);
        assertThat(link.getCode()).startsWith("plink_");

        PaymentLink paid = paymentLinks.pay(merchantId, link.getCode(), PaymentMethod.UPI, null, false);
        assertThat(paid.getStatus()).isEqualTo(PaymentLinkStatus.PAID);
        assertThat(paid.getPaymentId()).isNotNull();
        // Captured payment posts debit settlement / credit revenue.
        assertThat(balance(merchantId, "settlement")).isEqualTo(150_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(150_000L);
    }

    @Test
    void openAmountLinkTakesAmountAtPayTime() {
        UUID merchantId = newMerchant();
        PaymentLink link = paymentLinks.createLink(merchantId, "Donate", null, "INR", null, null);
        assertThat(link.isFixedAmount()).isFalse();

        paymentLinks.pay(merchantId, link.getCode(), PaymentMethod.CARD, 90_000L, false);
        assertThat(balance(merchantId, "settlement")).isEqualTo(90_000L);

        // Open link with no amount is rejected.
        PaymentLink link2 = paymentLinks.createLink(merchantId, "Donate2", null, "INR", null, null);
        assertThatThrownBy(() -> paymentLinks.pay(merchantId, link2.getCode(), PaymentMethod.CARD, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelledLinkCannotBePaid() {
        UUID merchantId = newMerchant();
        PaymentLink link = paymentLinks.createLink(merchantId, "One-time", 50_000L, "INR", null, null);
        paymentLinks.cancel(merchantId, link.getId());

        assertThatThrownBy(() -> paymentLinks.pay(merchantId, link.getCode(), PaymentMethod.UPI, null, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedPaymentLeavesLinkActive() {
        UUID merchantId = newMerchant();
        PaymentLink link = paymentLinks.createLink(merchantId, "Retryable", 50_000L, "INR", null, null);

        assertThatThrownBy(() -> paymentLinks.pay(merchantId, link.getCode(), PaymentMethod.UPI, null, true))
                .isInstanceOf(IllegalStateException.class);
        assertThat(paymentLinks.getLink(merchantId, link.getId()).getStatus()).isEqualTo(PaymentLinkStatus.ACTIVE);
    }

    @Test
    void getByCodeResolvesOwnLinkAndIsMerchantScoped() {
        UUID merchantA = newMerchant();
        UUID merchantB = newMerchant();
        PaymentLink link = paymentLinks.createLink(merchantA, "Invoice #7", 70_000L, "INR", "order-7", null);

        PaymentLink resolved = paymentLinks.getByCode(merchantA, link.getCode());
        assertThat(resolved.getId()).isEqualTo(link.getId());

        // merchant B cannot resolve merchant A's code.
        assertThatThrownBy(() -> paymentLinks.getByCode(merchantB, link.getCode()))
                .isInstanceOf(PaymentLinkNotFoundException.class);
    }

    private UUID newMerchant() {
        return merchants.create("pl-" + UUID.randomUUID().toString().substring(0, 8), "Links Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
