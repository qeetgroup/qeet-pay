package com.qeetgroup.qeetpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * De-duplication record for an inbound Razorpay webhook (TAD §7.1). Razorpay delivers at-least-once
 * and redelivers on any non-2xx, so every processed event id is persisted here and a replay is
 * short-circuited to a 200 no-op — the effects an event drives are applied exactly once.
 *
 * <p>Keyed on the globally-unique Razorpay event id ({@code x-razorpay-event-id}; a SHA-256 of the
 * raw body when the header is absent). Because the id is global — not per-tenant — and de-dup must
 * work before a merchant is resolved, the backing table carries <em>no</em> row-level security (see
 * {@code V37__razorpay_webhook_events.sql}), like the public routing map {@code link_public_lookup}.
 * {@code merchantId} is recorded for audit only and may be {@code null} when unresolved.
 */
@Entity
@Table(name = "razorpay_webhook_events", schema = "payments")
public class RazorpayWebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected RazorpayWebhookEvent() {}

    public RazorpayWebhookEvent(String eventId, String eventType, UUID merchantId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.merchantId = merchantId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}
