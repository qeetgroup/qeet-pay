package com.qeetgroup.qeetpay.treasury;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A per-merchant auto-sweep rule: move cash from a {@code source} ledger account into a {@code target}
 * account when the rule fires, retaining a {@code keepMinor} buffer in the source. Firing is driven by
 * a {@link SweepTrigger} — a balance threshold or a schedule. Only {@link SweepRuleStatus#ACTIVE}
 * rules are evaluated.
 */
@Entity
@Table(name = "sweep_rules", schema = "treasury")
public class SweepRule {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_account_code", nullable = false)
    private String sourceAccountCode;

    @Column(name = "target_account_code", nullable = false)
    private String targetAccountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private SweepTrigger trigger;

    /** Balance (minor units) above which a THRESHOLD rule sweeps; null for SCHEDULE rules. */
    @Column(name = "threshold_minor")
    private Long thresholdMinor;

    /** Free-form cadence for a SCHEDULE rule (e.g. {@code "daily"}); null for THRESHOLD rules. */
    @Column private String schedule;

    /** Minor units to always retain in the source account after a sweep. */
    @Column(name = "keep_minor", nullable = false)
    private long keepMinor = 0;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SweepRuleStatus status = SweepRuleStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SweepRule() {}

    public SweepRule(
            UUID merchantId,
            String name,
            String sourceAccountCode,
            String targetAccountCode,
            SweepTrigger trigger,
            Long thresholdMinor,
            String schedule,
            long keepMinor,
            String currency) {
        this.merchantId = merchantId;
        this.name = name;
        this.sourceAccountCode = sourceAccountCode;
        this.targetAccountCode = targetAccountCode;
        this.trigger = trigger;
        this.thresholdMinor = thresholdMinor;
        this.schedule = schedule;
        this.keepMinor = keepMinor;
        this.currency = currency;
    }

    public void pause() {
        this.status = SweepRuleStatus.PAUSED;
        this.updatedAt = Instant.now();
    }

    public void resume() {
        this.status = SweepRuleStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == SweepRuleStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getName() {
        return name;
    }

    public String getSourceAccountCode() {
        return sourceAccountCode;
    }

    public String getTargetAccountCode() {
        return targetAccountCode;
    }

    public SweepTrigger getTrigger() {
        return trigger;
    }

    public Long getThresholdMinor() {
        return thresholdMinor;
    }

    public String getSchedule() {
        return schedule;
    }

    public long getKeepMinor() {
        return keepMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public SweepRuleStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
