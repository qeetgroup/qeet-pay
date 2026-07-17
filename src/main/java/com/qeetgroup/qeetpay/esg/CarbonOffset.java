package com.qeetgroup.qeetpay.esg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a carbon-offset purchase. Offsetting {@code gramsCo2Offset} grams costs
 * {@code costMinor}; the {@code ledgerEntryId} is the balanced posting that paid for it, or null for a
 * zero-cost offset (which never touches the ledger).
 */
@Entity
@Table(name = "carbon_offsets", schema = "esg")
public class CarbonOffset {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "grams_co2_offset", nullable = false)
    private long gramsCo2Offset;

    @Column(name = "cost_minor", nullable = false)
    private long costMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CarbonOffset() {}

    public CarbonOffset(
            UUID merchantId, long gramsCo2Offset, long costMinor, String currency,
            UUID ledgerEntryId, String note) {
        this.merchantId = merchantId;
        this.gramsCo2Offset = gramsCo2Offset;
        this.costMinor = costMinor;
        this.currency = currency;
        this.ledgerEntryId = ledgerEntryId;
        this.note = note;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public long getGramsCo2Offset() { return gramsCo2Offset; }
    public long getCostMinor() { return costMinor; }
    public String getCurrency() { return currency; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
