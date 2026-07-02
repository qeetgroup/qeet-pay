package com.qeetgroup.qeetpay.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable payment fact written on CAPTURED, FAILED, or REFUNDED transitions. */
@Entity
@Table(name = "payment_events", schema = "analytics")
public class PaymentAnalyticsEvent {

    public static final String CAPTURED = "CAPTURED";
    public static final String FAILED   = "FAILED";
    public static final String REFUNDED = "REFUNDED";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    private String method;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected PaymentAnalyticsEvent() {}

    public PaymentAnalyticsEvent(UUID merchantId, UUID paymentId, String eventType, long amountMinor, String method) {
        this.merchantId  = merchantId;
        this.paymentId   = paymentId;
        this.eventType   = eventType;
        this.amountMinor = amountMinor;
        this.method      = method;
    }

    public UUID getId()           { return id; }
    public UUID getMerchantId()   { return merchantId; }
    public UUID getPaymentId()    { return paymentId; }
    public String getEventType()  { return eventType; }
    public long getAmountMinor()  { return amountMinor; }
    public String getMethod()     { return method; }
    public Instant getOccurredAt(){ return occurredAt; }
}
