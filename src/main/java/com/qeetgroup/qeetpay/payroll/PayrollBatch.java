package com.qeetgroup.qeetpay.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A payroll run (PRD Module 02.5): many employee lines created together from Qeet People and approved
 * or rejected as one maker-checker unit. On approval each line's net pay is disbursed through the
 * payouts engine; the batch records the aggregate outcome (all paid, partial, or all failed). Amounts
 * are integer minor units (paise).
 */
@Entity
@Table(name = "payroll_batches", schema = "payroll")
public class PayrollBatch {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String currency;

    @Column private String period;

    @Column private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollBatchStatus status = PayrollBatchStatus.PENDING_APPROVAL;

    @Column(name = "payout_batch_id")
    private UUID payoutBatchId;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "total_gross_minor", nullable = false)
    private long totalGrossMinor;

    @Column(name = "total_statutory_minor", nullable = false)
    private long totalStatutoryMinor;

    @Column(name = "total_net_minor", nullable = false)
    private long totalNetMinor;

    @Column(name = "paid_count", nullable = false)
    private int paidCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollBatch() {}

    public PayrollBatch(
            UUID merchantId,
            String currency,
            String period,
            String description,
            int lineCount,
            long totalGrossMinor,
            long totalStatutoryMinor,
            long totalNetMinor) {
        this.merchantId = merchantId;
        this.currency = currency;
        this.period = period;
        this.description = description;
        this.lineCount = lineCount;
        this.totalGrossMinor = totalGrossMinor;
        this.totalStatutoryMinor = totalStatutoryMinor;
        this.totalNetMinor = totalNetMinor;
    }

    /** Records the aggregate disbursal outcome after approval and links the underlying payout batch. */
    public void complete(UUID payoutBatchId, int paidCount, int failedCount) {
        this.payoutBatchId = payoutBatchId;
        this.paidCount = paidCount;
        this.failedCount = failedCount;
        if (failedCount == 0) {
            this.status = PayrollBatchStatus.DISBURSED;
        } else if (paidCount == 0) {
            this.status = PayrollBatchStatus.FAILED;
        } else {
            this.status = PayrollBatchStatus.PARTIALLY_DISBURSED;
        }
        touch();
    }

    public void reject() {
        this.status = PayrollBatchStatus.REJECTED;
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

    public String getPeriod() {
        return period;
    }

    public String getDescription() {
        return description;
    }

    public PayrollBatchStatus getStatus() {
        return status;
    }

    public UUID getPayoutBatchId() {
        return payoutBatchId;
    }

    public int getLineCount() {
        return lineCount;
    }

    public long getTotalGrossMinor() {
        return totalGrossMinor;
    }

    public long getTotalStatutoryMinor() {
        return totalStatutoryMinor;
    }

    public long getTotalNetMinor() {
        return totalNetMinor;
    }

    public int getPaidCount() {
        return paidCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
