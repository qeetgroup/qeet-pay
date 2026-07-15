package com.qeetgroup.qeetpay.revrec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A performance obligation being recognised over time (IndAS 115). Created with a balanced deferral
 * posting; {@code recognizedMinor} accumulates as periods fall due until it equals {@code totalMinor}
 * and the schedule COMPLETEs.
 */
@Entity
@Table(name = "recognition_schedules", schema = "revrec")
public class RecognitionSchedule {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(nullable = false)
    private String currency;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "recognized_minor", nullable = false)
    private long recognizedMinor = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecognitionMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecognitionStatus status = RecognitionStatus.SCHEDULED;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "deferral_entry_id", nullable = false)
    private UUID deferralEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected RecognitionSchedule() {}

    public RecognitionSchedule(
            UUID merchantId,
            String sourceType,
            String sourceRef,
            String currency,
            long totalMinor,
            RecognitionMethod method,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID deferralEntryId) {
        this.merchantId = merchantId;
        this.sourceType = sourceType;
        this.sourceRef = sourceRef;
        this.currency = currency;
        this.totalMinor = totalMinor;
        this.method = method;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.deferralEntryId = deferralEntryId;
    }

    /** Records that {@code amountMinor} was just recognised; advances status and clamps at the total. */
    public void applyRecognition(long amountMinor) {
        if (status == RecognitionStatus.CANCELLED || status == RecognitionStatus.COMPLETED) {
            throw new IllegalStateException("cannot recognise against a " + status + " schedule");
        }
        this.recognizedMinor += amountMinor;
        this.updatedAt = Instant.now();
        if (recognizedMinor >= totalMinor) {
            this.status = RecognitionStatus.COMPLETED;
            this.completedAt = Instant.now();
        } else {
            this.status = RecognitionStatus.IN_PROGRESS;
        }
    }

    public long remainingMinor() {
        return totalMinor - recognizedMinor;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public String getCurrency() { return currency; }
    public long getTotalMinor() { return totalMinor; }
    public long getRecognizedMinor() { return recognizedMinor; }
    public RecognitionMethod getMethod() { return method; }
    public RecognitionStatus getStatus() { return status; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public UUID getDeferralEntryId() { return deferralEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
