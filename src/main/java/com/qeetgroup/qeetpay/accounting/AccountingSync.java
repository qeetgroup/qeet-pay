package com.qeetgroup.qeetpay.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One export run to an external accounting system. Created {@code PENDING}, then transitioned to
 * {@code SUCCESS} (with the external reference, record count, and the generated document for
 * re-download) or {@code FAILED} (with a detail message). Merchant-scoped via platform RLS.
 */
@Entity
@Table(name = "accounting_syncs", schema = "accounting")
public class AccountingSync {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountingTarget target;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status = SyncStatus.PENDING;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "external_ref")
    private String externalRef;

    @Column private String detail;

    @Column private String document;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AccountingSync() {}

    public AccountingSync(UUID merchantId, AccountingTarget target, Instant periodStart, Instant periodEnd) {
        this.merchantId = merchantId;
        this.target = target;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public void markSuccess(int recordCount, String externalRef, String document) {
        this.status = SyncStatus.SUCCESS;
        this.recordCount = recordCount;
        this.externalRef = externalRef;
        this.document = document;
        this.completedAt = Instant.now();
    }

    public void markFailed(String detail) {
        this.status = SyncStatus.FAILED;
        this.detail = detail;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public AccountingTarget getTarget() { return target; }
    public Instant getPeriodStart() { return periodStart; }
    public Instant getPeriodEnd() { return periodEnd; }
    public SyncStatus getStatus() { return status; }
    public int getRecordCount() { return recordCount; }
    public String getExternalRef() { return externalRef; }
    public String getDetail() { return detail; }
    public String getDocument() { return document; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
