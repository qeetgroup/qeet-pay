package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A customer's subscription to a plan. Lifecycle: TRIALING → ACTIVE → PAST_DUE → CANCELLED/PAUSED. */
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

    @Column(name = "mandate_id")
    private UUID mandateId;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Subscription() {}

    public Subscription(UUID merchantId, UUID planId, String customerRef, Instant periodStart, Instant periodEnd) {
        this.merchantId = merchantId;
        this.planId = planId;
        this.customerRef = customerRef;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
    }

    public void startTrial(Instant trialEnd) {
        this.status = SubscriptionStatus.TRIALING;
        this.trialEndsAt = trialEnd;
    }

    public void activate() {
        if (status != SubscriptionStatus.TRIALING && status != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("cannot activate subscription in status " + status);
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.pausedAt = null;
    }

    public void upgradePlan(UUID newPlanId) {
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.TRIALING) {
            throw new IllegalStateException("cannot upgrade subscription in status " + status);
        }
        this.planId = newPlanId;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void pause() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("can only pause an ACTIVE subscription; status=" + status);
        }
        this.status = SubscriptionStatus.PAUSED;
        this.pausedAt = Instant.now();
    }

    public void resume() {
        if (status != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("can only resume a PAUSED subscription; status=" + status);
        }
        this.status = SubscriptionStatus.ACTIVE;
        this.pausedAt = null;
    }

    public void cancel(boolean atPeriodEnd) {
        if (status == SubscriptionStatus.CANCELLED) return; // idempotent
        if (atPeriodEnd) {
            this.cancelAtPeriodEnd = true;
        } else {
            this.status = SubscriptionStatus.CANCELLED;
            this.cancelledAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getPlanId() { return planId; }
    public String getCustomerRef() { return customerRef; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public UUID getMandateId() { return mandateId; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public Instant getPausedAt() { return pausedAt; }
    public Instant getCancelledAt() { return cancelledAt; }

    public void setMandateId(UUID mandateId) { this.mandateId = mandateId; }
    public void renewPeriod(Instant newStart, Instant newEnd) {
        this.currentPeriodStart = newStart;
        this.currentPeriodEnd = newEnd;
    }
}
