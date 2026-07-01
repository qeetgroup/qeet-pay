package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit trail for all subscription lifecycle transitions. */
@Entity
@Table(name = "subscription_events", schema = "billing")
public class SubscriptionEvent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    protected SubscriptionEvent() {}

    public SubscriptionEvent(UUID merchantId, UUID subscriptionId, String eventType, String metadata) {
        this.merchantId = merchantId;
        this.subscriptionId = subscriptionId;
        this.eventType = eventType;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
}
