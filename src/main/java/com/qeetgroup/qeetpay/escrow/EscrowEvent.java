package com.qeetgroup.qeetpay.escrow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of one hold / release / refund on an escrow agreement. */
@Entity
@Table(name = "escrow_events", schema = "escrow")
public class EscrowEvent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowEventType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected EscrowEvent() {}

    public EscrowEvent(
            UUID agreementId, UUID merchantId, EscrowEventType type, long amountMinor,
            UUID ledgerEntryId, String note) {
        this.agreementId = agreementId;
        this.merchantId = merchantId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.ledgerEntryId = ledgerEntryId;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getAgreementId() { return agreementId; }
    public EscrowEventType getType() { return type; }
    public long getAmountMinor() { return amountMinor; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
