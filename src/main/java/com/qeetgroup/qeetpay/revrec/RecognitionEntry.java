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

/** One period's slice of a {@link RecognitionSchedule}. Recognised when {@code periodEnd} falls due. */
@Entity
@Table(name = "recognition_entries", schema = "revrec")
public class RecognitionEntry {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecognitionEntryStatus status = RecognitionEntryStatus.PENDING;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "recognized_at")
    private Instant recognizedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RecognitionEntry() {}

    public RecognitionEntry(
            UUID scheduleId,
            UUID merchantId,
            int seq,
            LocalDate periodStart,
            LocalDate periodEnd,
            long amountMinor) {
        this.scheduleId = scheduleId;
        this.merchantId = merchantId;
        this.seq = seq;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amountMinor = amountMinor;
    }

    public void markRecognized(UUID ledgerEntryId) {
        this.status = RecognitionEntryStatus.RECOGNIZED;
        this.ledgerEntryId = ledgerEntryId;
        this.recognizedAt = Instant.now();
    }

    public boolean isPending() {
        return status == RecognitionEntryStatus.PENDING;
    }

    public UUID getId() { return id; }
    public UUID getScheduleId() { return scheduleId; }
    public UUID getMerchantId() { return merchantId; }
    public int getSeq() { return seq; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public long getAmountMinor() { return amountMinor; }
    public RecognitionEntryStatus getStatus() { return status; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getRecognizedAt() { return recognizedAt; }
}
