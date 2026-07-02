package com.qeetgroup.qeetpay.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Signed MRR-delta fact written on subscription lifecycle transitions. */
@Entity
@Table(name = "subscription_events", schema = "analytics")
public class SubscriptionAnalyticsEvent {

    public static final String NEW          = "NEW";
    public static final String EXPANSION    = "EXPANSION";
    public static final String CONTRACTION  = "CONTRACTION";
    public static final String CHURN        = "CHURN";
    public static final String REACTIVATION = "REACTIVATION";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** Signed paise: positive = growth (NEW/EXPANSION/REACTIVATION), negative = loss (CHURN/CONTRACTION). */
    @Column(name = "mrr_delta_minor", nullable = false)
    private long mrrDeltaMinor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected SubscriptionAnalyticsEvent() {}

    public SubscriptionAnalyticsEvent(UUID merchantId, UUID subscriptionId, String eventType, long mrrDeltaMinor) {
        this.merchantId      = merchantId;
        this.subscriptionId  = subscriptionId;
        this.eventType       = eventType;
        this.mrrDeltaMinor   = mrrDeltaMinor;
    }

    public UUID getId()              { return id; }
    public UUID getMerchantId()      { return merchantId; }
    public UUID getSubscriptionId()  { return subscriptionId; }
    public String getEventType()     { return eventType; }
    public long getMrrDeltaMinor()   { return mrrDeltaMinor; }
    public Instant getOccurredAt()   { return occurredAt; }
}
