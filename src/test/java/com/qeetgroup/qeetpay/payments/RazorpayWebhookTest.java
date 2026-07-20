package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Inbound Razorpay webhook processing (TAD §7.1). A signed {@code payment.captured} event resolves its
 * merchant from the order notes (no API key), captures the payment and posts the money-movement ledger
 * entry; a bad signature is rejected 400; a redelivered event (same event id) is a 200 no-op that never
 * double-posts.
 */
@SpringBootTest(
        properties = {
            "qeetpay.razorpay.enabled=true",
            "qeetpay.razorpay.key-id=rzp_test_key",
            "qeetpay.razorpay.key-secret=rzp_test_secret",
            "qeetpay.razorpay.webhook-secret=" + RazorpayWebhookTest.WEBHOOK_SECRET
        })
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
@Import(RazorpayWebhookTest.StubRazorpayConfig.class)
class RazorpayWebhookTest {

    static final String WEBHOOK_SECRET = "whsec_test_qeetpay";
    private static final String WEBHOOK_URL = "/v1/payments/razorpay/webhook";

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired LedgerService ledger;
    @Autowired MockMvc mvc;

    @Test
    void signedCapturedEventCapturesPaymentAndPostsLedger() throws Exception {
        UUID merchantId = newMerchant();
        Payment payment = payments.create(merchantId, 499900, "INR", PaymentMethod.UPI, "sale", false);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

        String body = capturedEventBody(merchantId, payment, "pay_capture_1");

        // No X-Merchant-Id, no X-Api-Key: the webhook resolves the merchant from the signed notes.
        MerchantContext.clear();
        mvc.perform(
                        post(WEBHOOK_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Razorpay-Signature", sign(body, WEBHOOK_SECRET))
                                .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID())
                                .content(body))
                .andExpect(status().isOk());

        assertThat(payments.get(merchantId, payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(499900);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(499900);
    }

    @Test
    void badSignatureIsRejected() throws Exception {
        UUID merchantId = newMerchant();
        Payment payment = payments.create(merchantId, 100000, "INR", PaymentMethod.CARD, "sale", false);
        String body = capturedEventBody(merchantId, payment, "pay_capture_2");

        MerchantContext.clear();
        mvc.perform(
                        post(WEBHOOK_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Razorpay-Signature", "deadbeefnotavalidsignature")
                                .header("X-Razorpay-Event-Id", "evt_" + UUID.randomUUID())
                                .content(body))
                .andExpect(status().isBadRequest());

        // Nothing was applied: the payment is still only authorized.
        assertThat(payments.get(merchantId, payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    void redeliveredEventIsNotAppliedTwice() throws Exception {
        UUID merchantId = newMerchant();
        Payment payment = payments.create(merchantId, 250000, "INR", PaymentMethod.UPI, "sale", false);
        String body = capturedEventBody(merchantId, payment, "pay_capture_3");
        String signature = sign(body, WEBHOOK_SECRET);
        String eventId = "evt_" + UUID.randomUUID(); // SAME id on both deliveries

        MerchantContext.clear();
        mvc.perform(
                        post(WEBHOOK_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", eventId)
                                .content(body))
                .andExpect(status().isOk());

        MerchantContext.clear();
        mvc.perform(
                        post(WEBHOOK_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", eventId)
                                .content(body))
                .andExpect(status().isOk()); // duplicate → 200 no-op

        assertThat(payments.get(merchantId, payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        // The capture was posted exactly once despite the redelivery.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(250000);
    }

    private static String capturedEventBody(UUID merchantId, Payment payment, String razorpayPaymentId) {
        return "{\"entity\":\"event\",\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"id\":\"" + razorpayPaymentId + "\","
                + "\"order_id\":\"" + payment.getProviderPaymentId() + "\","
                + "\"amount\":" + payment.getAmountMinor() + ","
                + "\"currency\":\"INR\",\"status\":\"captured\","
                + "\"notes\":{\"merchant_id\":\"" + merchantId + "\",\"payment_id\":\"" + payment.getId() + "\"}"
                + "}}}}";
    }

    private static String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private UUID newMerchant() {
        return merchants.create("wh-" + UUID.randomUUID().toString().substring(0, 8), "Webhook Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }

    @TestConfiguration
    static class StubRazorpayConfig {

        /** Stub gateway so enabling Razorpay makes no real network calls during create(). */
        @Bean
        @Primary
        RazorpayGateway stubRazorpayGateway() {
            return new RazorpayGateway() {
                @Override
                public String createOrder(long amountMinor, String currency, UUID merchantId, UUID paymentId) {
                    return "order_stub_" + paymentId.toString().substring(0, 8);
                }

                @Override
                public String capturePayment(String paymentId, long amountMinor, String currency) {
                    return "pay_stub_" + UUID.randomUUID().toString().substring(0, 8);
                }

                @Override
                public String refundPayment(String paymentId, long amountMinor) {
                    return "rfnd_stub_" + UUID.randomUUID().toString().substring(0, 8);
                }
            };
        }
    }
}
