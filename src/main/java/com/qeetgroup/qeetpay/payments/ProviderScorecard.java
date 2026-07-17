package com.qeetgroup.qeetpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-merchant, per-provider routing scorecard (PRD Module 07.3). Accumulates the outcome of every
 * provider call into a rolling authorization rate and a health signal, plus a configurable cost, so
 * {@link ProviderScorer} can rank acquirers. Health trips DEGRADED at 2 consecutive failures and DOWN
 * at 5; any success restores it to HEALTHY.
 */
@Entity
@Table(name = "provider_scorecards", schema = "payments")
public class ProviderScorecard {

    static final int DEGRADE_THRESHOLD = 2;
    static final int DOWN_THRESHOLD = 5;

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private long attempts = 0;

    @Column(nullable = false)
    private long successes = 0;

    @Column(nullable = false)
    private long failures = 0;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "cost_bps", nullable = false)
    private int costBps = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderHealth health = ProviderHealth.HEALTHY;

    @Column(name = "last_outcome_at")
    private Instant lastOutcomeAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProviderScorecard() {}

    public ProviderScorecard(UUID merchantId, String provider) {
        this.merchantId = merchantId;
        this.provider = provider;
    }

    public void recordSuccess() {
        this.attempts++;
        this.successes++;
        this.consecutiveFailures = 0;
        this.health = ProviderHealth.HEALTHY;
        touch();
    }

    public void recordFailure() {
        this.attempts++;
        this.failures++;
        this.consecutiveFailures++;
        if (consecutiveFailures >= DOWN_THRESHOLD) {
            this.health = ProviderHealth.DOWN;
        } else if (consecutiveFailures >= DEGRADE_THRESHOLD) {
            this.health = ProviderHealth.DEGRADED;
        }
        touch();
    }

    public void setCostBps(int costBps) {
        if (costBps < 0 || costBps > 10_000) {
            throw new IllegalArgumentException("costBps must be between 0 and 10000");
        }
        this.costBps = costBps;
        touch();
    }

    private void touch() {
        this.lastOutcomeAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Authorization rate in [0,1]. An unused provider is treated optimistically (1.0). */
    public double authRate() {
        return attempts == 0 ? 1.0 : (double) successes / attempts;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getProvider() { return provider; }
    public long getAttempts() { return attempts; }
    public long getSuccesses() { return successes; }
    public long getFailures() { return failures; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getCostBps() { return costBps; }
    public ProviderHealth getHealth() { return health; }
    public Instant getLastOutcomeAt() { return lastOutcomeAt; }
}
