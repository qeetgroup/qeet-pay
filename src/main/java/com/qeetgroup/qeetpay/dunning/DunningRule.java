package com.qeetgroup.qeetpay.dunning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Merchant-configured retry policy for failed subscription payments.
 * {@code failureCodePattern} is a simple wildcard ('*' = catch-all; literal = exact match).
 */
@Entity
@Table(name = "rules", schema = "dunning")
public class DunningRule {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "failure_code_pattern", nullable = false)
    private String failureCodePattern = "*";

    @Column(name = "retry_interval_hours", nullable = false)
    private int retryIntervalHours = 24;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notify_channels", columnDefinition = "jsonb")
    private String notifyChannels;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DunningRule() {}

    public DunningRule(UUID merchantId, String name, String failureCodePattern,
            int retryIntervalHours, int maxAttempts, String notifyChannels) {
        this.merchantId = merchantId;
        this.name = name;
        this.failureCodePattern = failureCodePattern != null ? failureCodePattern : "*";
        this.retryIntervalHours = retryIntervalHours;
        this.maxAttempts = maxAttempts;
        this.notifyChannels = notifyChannels;
    }

    public boolean matches(String failureCode) {
        if ("*".equals(failureCodePattern)) return true;
        return failureCodePattern != null && failureCodePattern.equals(failureCode);
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getName() { return name; }
    public String getFailureCodePattern() { return failureCodePattern; }
    public int getRetryIntervalHours() { return retryIntervalHours; }
    public int getMaxAttempts() { return maxAttempts; }
    public String getNotifyChannels() { return notifyChannels; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
}
