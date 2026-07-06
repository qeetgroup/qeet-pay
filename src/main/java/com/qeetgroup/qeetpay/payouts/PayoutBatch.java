package com.qeetgroup.qeetpay.payouts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A bulk-payout batch (TAD Module 02 / §17): many payouts created together and approved or rejected
 * as one maker-checker unit. On approval each member payout is disbursed independently; the batch
 * records the aggregate outcome (all paid, partial, or all failed).
 */
@Entity
@Table(name = "payout_batches", schema = "payouts")
public class PayoutBatch {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String currency;

    @Column private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status = BatchStatus.PENDING_APPROVAL;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "total_amount_minor", nullable = false)
    private long totalAmountMinor;

    @Column(name = "paid_count", nullable = false)
    private int paidCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayoutBatch() {}

    public PayoutBatch(UUID merchantId, String currency, String description, int totalCount, long totalAmountMinor) {
        this.merchantId = merchantId;
        this.currency = currency;
        this.description = description;
        this.totalCount = totalCount;
        this.totalAmountMinor = totalAmountMinor;
    }

    /** Records the aggregate disbursal outcome after approval. */
    public void complete(int paidCount, int failedCount) {
        this.paidCount = paidCount;
        this.failedCount = failedCount;
        if (failedCount == 0) {
            this.status = BatchStatus.COMPLETED;
        } else if (paidCount == 0) {
            this.status = BatchStatus.FAILED;
        } else {
            this.status = BatchStatus.PARTIALLY_COMPLETED;
        }
        touch();
    }

    public void reject() {
        this.status = BatchStatus.REJECTED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public long getTotalAmountMinor() {
        return totalAmountMinor;
    }

    public int getPaidCount() {
        return paidCount;
    }

    public int getFailedCount() {
        return failedCount;
    }
}
