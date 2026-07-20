package com.qeetgroup.qeetpay.agentic;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A scoped, revocable grant of authority to one AI agent: what operations + payees it may act on, a
 * per-transaction cap and a cumulative spend cap, a validity window, and a running spent counter.
 * Authorization decisions are made deterministically by {@link AgentMandateService}; the mandate
 * itself only holds state and enforces its invariants ({@code spent <= cumulativeCap}).
 */
@Entity
@Table(name = "agent_mandates", schema = "agentic")
public class AgentMandate {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column private String label;

    @Column(name = "max_txn_minor", nullable = false)
    private long maxTxnMinor;

    @Column(name = "cumulative_cap_minor", nullable = false)
    private long cumulativeCapMinor;

    @Column(name = "spent_minor", nullable = false)
    private long spentMinor = 0;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_operations", nullable = false)
    private List<String> allowedOperations = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_payees", nullable = false)
    private List<String> allowedPayees = List.of();

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentMandateStatus status = AgentMandateStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected AgentMandate() {}

    public AgentMandate(
            UUID merchantId,
            String agentId,
            String label,
            long maxTxnMinor,
            long cumulativeCapMinor,
            List<String> allowedOperations,
            List<String> allowedPayees,
            Instant validFrom,
            Instant expiresAt) {
        this.merchantId = merchantId;
        this.agentId = agentId;
        this.label = label;
        this.maxTxnMinor = maxTxnMinor;
        this.cumulativeCapMinor = cumulativeCapMinor;
        this.allowedOperations = allowedOperations == null ? List.of() : List.copyOf(allowedOperations);
        this.allowedPayees = allowedPayees == null ? List.of() : List.copyOf(allowedPayees);
        if (validFrom != null) {
            this.validFrom = validFrom;
        }
        this.expiresAt = expiresAt;
    }

    /** Cap headroom left before the cumulative spend cap is reached. */
    public long remainingMinor() {
        return cumulativeCapMinor - spentMinor;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public boolean isNotYetValid(Instant now) {
        return validFrom != null && now.isBefore(validFrom);
    }

    /** Adds a captured amount to the running spend; guards the cumulative cap invariant. */
    public void recordSpend(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (spentMinor + amountMinor > cumulativeCapMinor) {
            throw new IllegalStateException("spend would exceed cumulative cap");
        }
        this.spentMinor += amountMinor;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.status = AgentMandateStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void revoke() {
        this.status = AgentMandateStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getAgentId() { return agentId; }
    public String getLabel() { return label; }
    public long getMaxTxnMinor() { return maxTxnMinor; }
    public long getCumulativeCapMinor() { return cumulativeCapMinor; }
    public long getSpentMinor() { return spentMinor; }
    public List<String> getAllowedOperations() { return allowedOperations; }
    public List<String> getAllowedPayees() { return allowedPayees; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getExpiresAt() { return expiresAt; }
    public AgentMandateStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
