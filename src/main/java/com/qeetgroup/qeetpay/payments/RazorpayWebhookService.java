package com.qeetgroup.qeetpay.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes an authenticity-verified Razorpay webhook into the domain (TAD §7.1). The controller has
 * already checked the HMAC-SHA256 signature; this service parses the event and drives the
 * corresponding state change, reusing {@link PaymentService} so the ledger logic is never duplicated.
 *
 * <p><b>Merchant resolution without an API key.</b> A webhook is inbound from Razorpay and carries no
 * merchant credential, so — exactly like the public hosted-checkout path ({@code checkout/
 * CheckoutService}) — the merchant is resolved before any RLS-scoped access, then the scope is applied
 * (inside the {@code PaymentService} methods) so Postgres RLS is satisfied. The tenant is taken from
 * the {@code notes} we embed on every Razorpay order at creation ({@code merchant_id}/{@code
 * payment_id}; see {@code DefaultRazorpayGateway}); the HMAC check makes those notes trustworthy.
 *
 * <p><b>Idempotency.</b> Every processed event id is recorded in {@code payments.razorpay_webhook_events}
 * (keyed on the global {@code x-razorpay-event-id}, or a body hash). A redelivered event whose id is
 * already present is a 200 no-op. Recording and the domain change commit in one transaction, so a
 * failed handler rolls both back and Razorpay's retry re-processes cleanly.
 */
@Service
class RazorpayWebhookService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);

    /** Outcome of processing an inbound event, for the controller's logging. */
    enum Result {
        PROCESSED,
        DUPLICATE
    }

    private final PaymentService paymentService;
    private final RazorpayWebhookEventRepository processedEvents;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    RazorpayWebhookService(
            PaymentService paymentService,
            RazorpayWebhookEventRepository processedEvents,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    Result process(String rawBody, String eventId) {
        if (processedEvents.existsById(eventId)) {
            log.info("Razorpay webhook {} already processed — 200 no-op", eventId);
            return Result.DUPLICATE;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("unparseable Razorpay webhook body", e);
        }

        String eventType = text(root, "event");
        JsonNode payload = root.path("payload");
        UUID merchantId = dispatch(eventType, payload);

        processedEvents.save(
                new RazorpayWebhookEvent(eventId, eventType == null ? "unknown" : eventType, merchantId));
        return Result.PROCESSED;
    }

    /** Routes an event to its handler; returns the resolved merchant (nullable, for audit). */
    private UUID dispatch(String eventType, JsonNode payload) {
        if (eventType == null) {
            log.warn("Razorpay webhook missing 'event' field — acking 200");
            return null;
        }
        return switch (eventType) {
            case "payment.captured", "payment.authorized" -> handleCapture(payload);
            case "payment.failed" -> handleFailed(payload);
            case "refund.processed", "refund.created" -> handleRefund(eventType, payload);
            case "settlement.processed" -> handleSettlement(payload);
            default -> {
                log.info("Razorpay webhook: unhandled event type '{}' — acking 200", eventType);
                yield null;
            }
        };
    }

    private UUID handleCapture(JsonNode payload) {
        JsonNode entity = payload.path("payment").path("entity");
        UUID merchantId = noteUuid(entity, "merchant_id");
        if (merchantId == null) {
            log.warn("Razorpay payment webhook has no merchant_id note — acking 200");
            return null;
        }
        Optional<Payment> payment =
                resolvePayment(merchantId, noteUuid(entity, "payment_id"), text(entity, "order_id"));
        if (payment.isEmpty()) {
            log.warn(
                    "Razorpay capture webhook: no matching payment (merchant={} order={} rzpPayment={})",
                    merchantId, text(entity, "order_id"), text(entity, "id"));
            return merchantId;
        }
        paymentService.captureFromWebhook(merchantId, payment.get().getId(), text(entity, "id"));
        return merchantId;
    }

    private UUID handleFailed(JsonNode payload) {
        JsonNode entity = payload.path("payment").path("entity");
        UUID merchantId = noteUuid(entity, "merchant_id");
        if (merchantId == null) {
            log.warn("Razorpay payment.failed webhook has no merchant_id note — acking 200");
            return null;
        }
        Optional<Payment> payment =
                resolvePayment(merchantId, noteUuid(entity, "payment_id"), text(entity, "order_id"));
        if (payment.isEmpty()) {
            log.warn("Razorpay payment.failed webhook: no matching payment (merchant={})", merchantId);
            return merchantId;
        }
        String reason = firstNonBlank(text(entity, "error_description"), text(entity, "error_reason"));
        paymentService.markFailedFromWebhook(
                merchantId, payment.get().getId(), reason == null ? "razorpay_payment_failed" : reason);
        return merchantId;
    }

    private UUID handleRefund(String eventType, JsonNode payload) {
        JsonNode entity = payload.path("refund").path("entity");
        UUID merchantId = noteUuid(entity, "merchant_id");
        if (merchantId == null) {
            log.warn("Razorpay refund webhook has no merchant_id note — acking 200");
            return null;
        }
        String providerRefundId = text(entity, "id");
        String status = eventType.substring(eventType.indexOf('.') + 1); // processed | created
        boolean found = paymentService.recordRefundWebhook(merchantId, providerRefundId, status);
        if (!found) {
            log.warn(
                    "Razorpay {} webhook: no matching refund {} (out-of-band?) — acking 200",
                    eventType, providerRefundId);
        }
        return merchantId;
    }

    private UUID handleSettlement(JsonNode payload) {
        JsonNode entity = payload.path("settlement").path("entity");
        String settlementId = text(entity, "id");
        UUID merchantId = noteUuid(entity, "merchant_id");
        if (merchantId == null) {
            // Settlement events are account-level and rarely carry our order notes. Full ingestion
            // needs the line-level report (SettlementService.ingest), which arrives via the settlements
            // API — not this webhook. Ack without over-building.
            log.info(
                    "Razorpay settlement.processed {} without merchant note — acking 200 (report ingest is a separate step)",
                    settlementId);
            return null;
        }
        outbox.enqueue(merchantId, "settlement.processed", settlementJson(settlementId, entity));
        log.info(
                "Razorpay settlement.processed {} for merchant {} — emitted outbox event (report ingest handled by reconciliation)",
                settlementId, merchantId);
        return merchantId;
    }

    private Optional<Payment> resolvePayment(
            UUID merchantId, UUID internalPaymentId, String providerRef) {
        if (internalPaymentId != null) {
            Optional<Payment> byId = paymentService.find(merchantId, internalPaymentId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        return paymentService.findByProviderRef(merchantId, providerRef);
    }

    private String settlementJson(String settlementId, JsonNode entity) {
        try {
            return objectMapper.writeValueAsString(
                    objectMapper
                            .createObjectNode()
                            .put("providerSettlementId", settlementId)
                            .put("amountMinor", entity.path("amount").asLong(0))
                            .put("status", text(entity, "status")));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialise settlement event", e);
        }
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────────

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    /** Reads a UUID from the entity's {@code notes} object; null when absent or malformed. */
    private static UUID noteUuid(JsonNode entity, String noteKey) {
        String raw = text(entity.path("notes"), noteKey);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
