package com.qeetgroup.qeetpay.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One reconciliation run over a settlement (1:1 in Phase 1). Append-only: its outcome is fixed at
 * {@link #complete}. MATCHED when every line ties out; DISCREPANCY when any line or invariant fails.
 */
@Entity
@Table(name = "reconciliations", schema = "reconciliation")
public class Reconciliation {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReconciliationStatus status;

    @Column(name = "matched_count", nullable = false)
    private int matchedCount;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Reconciliation() {}

    public Reconciliation(UUID merchantId, UUID settlementId) {
        this.merchantId = merchantId;
        this.settlementId = settlementId;
    }

    /** Finalises the run: clean when no discrepancies were found. Call before persisting. */
    public void complete(int matchedCount, int discrepancyCount) {
        this.matchedCount = matchedCount;
        this.discrepancyCount = discrepancyCount;
        this.status =
                discrepancyCount == 0 ? ReconciliationStatus.MATCHED : ReconciliationStatus.DISCREPANCY;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public int getMatchedCount() {
        return matchedCount;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }
}
