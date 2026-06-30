package com.qeetgroup.qeetpay.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional-outbox row (TAD §9.1). Domain services enqueue an event in the <em>same</em>
 * transaction as the state change, guaranteeing no event is lost or emitted before commit. The
 * {@code NatsEventRelay} later publishes unsent rows to {@code pay.{merchant_id}.events.{type}}.
 */
@Entity
@Table(name = "outbox_event", schema = "platform")
public class OutboxEvent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String subject;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID merchantId, String subject, String eventType, String payload) {
        this.merchantId = merchantId;
        this.subject = subject;
        this.eventType = eventType;
        this.payload = payload;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getPayload() {
        return payload;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }
}
