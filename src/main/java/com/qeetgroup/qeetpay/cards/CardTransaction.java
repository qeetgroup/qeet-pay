package com.qeetgroup.qeetpay.cards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of one load / spend / refund on a virtual card. */
@Entity
@Table(name = "card_transactions", schema = "cards")
public class CardTransaction {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardTxnType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CardTransaction() {}

    public CardTransaction(
            UUID cardId, UUID merchantId, CardTxnType type, long amountMinor,
            UUID ledgerEntryId, String description) {
        this.cardId = cardId;
        this.merchantId = merchantId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.ledgerEntryId = ledgerEntryId;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getCardId() { return cardId; }
    public CardTxnType getType() { return type; }
    public long getAmountMinor() { return amountMinor; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
