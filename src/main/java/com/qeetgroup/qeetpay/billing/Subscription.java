package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A customer's subscription to a plan. */
@Entity
@Table(name = "subscriptions", schema = "billing")
public class Subscription {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Subscription() {}

    public Subscription(
            UUID merchantId, UUID planId, String customerRef, Instant periodStart, Instant periodEnd) {
        this.merchantId = merchantId;
        this.planId = planId;
        this.customerRef = customerRef;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}
