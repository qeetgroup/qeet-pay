package com.qeetgroup.qeetpay.crossborder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of one transition (created / remitted / failed) on an outbound remittance. */
@Entity
@Table(name = "outbound_remittance_events", schema = "crossborder")
public class OutboundRemittanceEvent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "remittance_id", nullable = false)
    private UUID remittanceId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboundRemittanceEventType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected OutboundRemittanceEvent() {}

    public OutboundRemittanceEvent(
            UUID remittanceId, UUID merchantId, OutboundRemittanceEventType type, long amountMinor,
            UUID ledgerEntryId, String note) {
        this.remittanceId = remittanceId;
        this.merchantId = merchantId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.ledgerEntryId = ledgerEntryId;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getRemittanceId() { return remittanceId; }
    public UUID getMerchantId() { return merchantId; }
    public OutboundRemittanceEventType getType() { return type; }
    public long getAmountMinor() { return amountMinor; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
