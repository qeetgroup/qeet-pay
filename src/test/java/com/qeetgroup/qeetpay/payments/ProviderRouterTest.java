package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Provider routing (TAD §7): with no Razorpay config the router delegates to the sandbox.
 * The provider_transactions audit record is written atomically with each operation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProviderRouterTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired ProviderTransactionRepository providerTxns;

    @Test
    void sandboxRouteAuditsProviderTransaction() {
        UUID merchantId = newMerchant();
        Payment payment = payments.create(merchantId, 200000, "INR", PaymentMethod.UPI, "test", false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getProviderPaymentId()).startsWith("sbx_auth_");

        // provider_transactions must contain one AUTHORIZE record for this payment
        var txns = providerTxns.findAll().stream()
                .filter(t -> t.getPaymentId().equals(payment.getId()))
                .toList();
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).getProviderName()).isEqualTo("SANDBOX");
        assertThat(txns.get(0).getOperation()).isEqualTo("AUTHORIZE");
        assertThat(txns.get(0).isSuccess()).isTrue();
    }

    @Test
    void captureAlsoAuditsProviderTransaction() {
        UUID merchantId = newMerchant();
        Payment authorized = payments.create(merchantId, 100000, "INR", PaymentMethod.CARD, "order", false);
        payments.capture(merchantId, authorized.getId());

        var txns = providerTxns.findAll().stream()
                .filter(t -> t.getPaymentId().equals(authorized.getId()))
                .toList();
        assertThat(txns).hasSize(2); // AUTHORIZE + CAPTURE
        assertThat(txns.stream().map(ProviderTransaction::getOperation).toList())
                .containsExactlyInAnyOrder("AUTHORIZE", "CAPTURE");
    }

    @Test
    void simulatedFailureAuditsFailedAuthorize() {
        UUID merchantId = newMerchant();
        Payment failed = payments.create(merchantId, 50000, "INR", PaymentMethod.UPI, "bad", true);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);

        var txns = providerTxns.findAll().stream()
                .filter(t -> t.getPaymentId().equals(failed.getId()))
                .toList();
        assertThat(txns).hasSize(1);
        assertThat(txns.get(0).isSuccess()).isFalse();
    }

    private UUID newMerchant() {
        return merchants.create("rtr-" + UUID.randomUUID().toString().substring(0, 8), "Router Co")
                .merchant()
                .getId();
    }
}
