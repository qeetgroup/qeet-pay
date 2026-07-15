package com.qeetgroup.qeetpay.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.Map;
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
 * Messaging flow (PRD Module 09): a WhatsApp template is configured then dispatched (rendered +
 * QUEUED), and a delivery callback marks it SENT. Dispatching without an active template is rejected.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MessagingFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired MessagingService messaging;

    @Test
    void configureRenderDispatchAndDeliver() {
        UUID merchantId = newMerchant();
        messaging.upsertTemplate(
                merchantId, "invoice", MessageChannel.WHATSAPP,
                "Hi {{name}}, your invoice {{number}} for {{amount}} is ready. Pay: {{link}}");

        MessageDispatch dispatch =
                messaging.dispatch(
                        merchantId, "invoice", MessageChannel.WHATSAPP, "+919876543210",
                        Map.of("name", "Asha", "number", "QP/2026-27/00042", "amount", "₹1,180",
                                "link", "https://pay.qeet.in/x"),
                        "invoice-42");

        assertThat(dispatch.getStatus()).isEqualTo(DispatchStatus.QUEUED);
        assertThat(dispatch.getRenderedBody())
                .isEqualTo("Hi Asha, your invoice QP/2026-27/00042 for ₹1,180 is ready. Pay: https://pay.qeet.in/x");
        assertThat(dispatch.getRelatedRef()).isEqualTo("invoice-42");

        MessageDispatch sent = messaging.markDelivered(merchantId, dispatch.getId(), "wamid.abc123");
        assertThat(sent.getStatus()).isEqualTo(DispatchStatus.SENT);
        assertThat(sent.getProviderRef()).isEqualTo("wamid.abc123");
        assertThat(sent.getSentAt()).isNotNull();
    }

    @Test
    void upsertUpdatesExistingTemplate() {
        UUID merchantId = newMerchant();
        messaging.upsertTemplate(merchantId, "dunning_reminder", MessageChannel.SMS, "v1 {{x}}");
        messaging.upsertTemplate(merchantId, "dunning_reminder", MessageChannel.SMS, "v2 {{x}}");

        assertThat(messaging.listTemplates(merchantId)).hasSize(1);
        MessageDispatch d =
                messaging.dispatch(merchantId, "dunning_reminder", MessageChannel.SMS, "+91999",
                        Map.of("x", "Y"), null);
        assertThat(d.getRenderedBody()).isEqualTo("v2 Y");
    }

    @Test
    void dispatchWithoutTemplateIsRejected() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() ->
                        messaging.dispatch(merchantId, "nope", MessageChannel.WHATSAPP, "+91", Map.of(), null))
                .isInstanceOf(MessagingNotFoundException.class);
    }

    @Test
    void failedDeliveryMarksFailed() {
        UUID merchantId = newMerchant();
        messaging.upsertTemplate(merchantId, "payout_confirmation", MessageChannel.WHATSAPP, "Paid {{amt}}");
        MessageDispatch d =
                messaging.dispatch(merchantId, "payout_confirmation", MessageChannel.WHATSAPP, "+91",
                        Map.of("amt", "₹500"), "payout-1");

        MessageDispatch failed = messaging.markFailed(merchantId, d.getId(), "invalid_number");
        assertThat(failed.getStatus()).isEqualTo(DispatchStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("invalid_number");
    }

    private UUID newMerchant() {
        return merchants.create("msg-" + UUID.randomUUID().toString().substring(0, 8), "Messaging Co")
                .merchant().getId();
    }
}
