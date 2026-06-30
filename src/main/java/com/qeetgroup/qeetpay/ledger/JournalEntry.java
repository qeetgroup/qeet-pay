package com.qeetgroup.qeetpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One balanced transaction in the ledger (its lines net to zero). Append-only: never updated or
 * deleted — corrections are posted as offsetting entries (TAD §6.2).
 */
@Entity
@Table(name = "journal_entries", schema = "ledger")
public class JournalEntry {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected JournalEntry() {}

    public JournalEntry(UUID merchantId, String description, String currency) {
        this.merchantId = merchantId;
        this.description = description;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
