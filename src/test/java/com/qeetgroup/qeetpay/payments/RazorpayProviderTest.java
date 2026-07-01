package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.qeetgroup.qeetpay.merchants.MerchantService;

/**
 * RazorpayPaymentProvider behaviour via a stub {@link RazorpayGateway}. Verifies that the provider
 * translates gateway responses into {@link PaymentProvider.ProviderResult} correctly, and that the
 * order-id returned by authorize ends up in {@code providerPaymentId}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(RazorpayProviderTest.StubRazorpayConfig.class)
class RazorpayProviderTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;

    @Test
    void authorizeViaRazorpayReturnsOrderId() {
        UUID merchantId = newMerchant();
        Payment payment = payments.create(merchantId, 499900, "INR", PaymentMethod.UPI, "razorpay-order", false);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        // stub gateway returns "rp_order_stub_<uuid>" — verify the prefix
        assertThat(payment.getProviderPaymentId()).startsWith("rp_order_stub_");
    }

    @Test
    void captureViaRazorpayPostsLedgerEntry() {
        UUID merchantId = newMerchant();
        Payment auth = payments.create(merchantId, 100000, "INR", PaymentMethod.CARD, "rp-cap", false);
        Payment captured = payments.capture(merchantId, auth.getId());

        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(captured.getLedgerEntryId()).isNotNull();
    }

    @Test
    void simulatedFailureRoutesThroughRazorpay() {
        UUID merchantId = newMerchant();
        Payment failed = payments.create(merchantId, 50000, "INR", PaymentMethod.UPI, "fail", true);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private UUID newMerchant() {
        return merchants.create("rzp-" + UUID.randomUUID().toString().substring(0, 8), "Rzp Co")
                .merchant()
                .getId();
    }

    @TestConfiguration
    static class StubRazorpayConfig {

        /** Stub gateway — no real Razorpay API calls in tests. */
        @Bean
        @Primary
        RazorpayGateway stubRazorpayGateway() {
            return new RazorpayGateway() {
                @Override
                public String createOrder(long amountMinor, String currency, UUID merchantId, UUID paymentId) {
                    return "rp_order_stub_" + paymentId.toString().substring(0, 8);
                }

                @Override
                public String capturePayment(String paymentId, long amountMinor, String currency) {
                    return "rp_pay_stub_" + UUID.randomUUID().toString().substring(0, 8);
                }

                @Override
                public String refundPayment(String paymentId, long amountMinor) {
                    return "rp_refund_stub_" + UUID.randomUUID().toString().substring(0, 8);
                }
            };
        }

        /** Explicitly create the Razorpay provider using the stub gateway (bypasses @ConditionalOnProperty). */
        @Bean
        RazorpayPaymentProvider razorpayPaymentProvider(RazorpayGateway gateway) {
            return new RazorpayPaymentProvider(gateway);
        }
    }
}
