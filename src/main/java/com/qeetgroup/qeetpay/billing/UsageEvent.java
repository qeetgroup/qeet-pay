package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable usage record for PER_UNIT / TIERED / VOLUME / HYBRID plans. */
@Entity
@Table(name = "usage_events", schema = "billing")
public class UsageEvent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "metric_key", nullable = false)
    private String metricKey;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "event_ts", nullable = false)
    private Instant eventTs = Instant.now();

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected UsageEvent() {}

    public UsageEvent(UUID merchantId, UUID subscriptionId, String metricKey, long quantity, String idempotencyKey) {
        this.merchantId = merchantId;
        this.subscriptionId = subscriptionId;
        this.metricKey = metricKey;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public String getMetricKey() { return metricKey; }
    public long getQuantity() { return quantity; }
    public Instant getEventTs() { return eventTs; }
}
