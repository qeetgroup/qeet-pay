package com.qeetgroup.qeetpay.dunning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable record of one dunning retry attempt for a PAST_DUE subscription. */
@Entity
@Table(name = "attempts", schema = "dunning")
public class DunningAttempt {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED  = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "attempted_at")
    private Instant attemptedAt;

    @Column
    private String result;

    @Column(name = "failure_reason")
    private String failureReason;

    // ── AI dunning classification (PRD Module 04.1) ──────────────────────────

    @Column(name = "failure_category")
    private String failureCategory;

    @Column(name = "recommended_delay_hours")
    private Integer recommendedDelayHours;

    @Column(name = "recommended_channels")
    private String recommendedChannels;

    @Column(name = "classification_rationale")
    private String classificationRationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DunningAttempt() {}

    public DunningAttempt(UUID merchantId, UUID subscriptionId, UUID ruleId, int attemptNumber, Instant scheduledAt) {
        this.merchantId = merchantId;
        this.subscriptionId = subscriptionId;
        this.ruleId = ruleId;
        this.attemptNumber = attemptNumber;
        this.scheduledAt = scheduledAt;
    }

    public void recordResult(String result, String failureReason) {
        this.attemptedAt = Instant.now();
        this.result = result;
        this.failureReason = failureReason;
    }

    /**
     * Attaches the classifier's decision. Must be called before the attempt is first persisted
     * ({@code dunning.attempts} is append-only — SELECT/INSERT only, per V11).
     */
    public void applyClassification(RetryRecommendation rec) {
        this.failureCategory = rec.category().name();
        this.recommendedDelayHours = rec.recommendedDelayHours();
        this.recommendedChannels = rec.recommendedChannels();
        this.classificationRationale = rec.rationale();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getRuleId() { return ruleId; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public String getResult() { return result; }
    public String getFailureReason() { return failureReason; }
    public String getFailureCategory() { return failureCategory; }
    public Integer getRecommendedDelayHours() { return recommendedDelayHours; }
    public String getRecommendedChannels() { return recommendedChannels; }
    public String getClassificationRationale() { return classificationRationale; }
}
