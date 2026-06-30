package com.qeetgroup.qeetpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single debit or credit against an account, in integer minor units (e.g. paise). Money is never
 * a float; rupee↔minor conversion uses {@code BigDecimal} (HALF_UP) at the API boundary.
 */
@Entity
@Table(name = "journal_lines", schema = "ledger")
public class JournalLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected JournalLine() {}

    public JournalLine(UUID entryId, UUID merchantId, UUID accountId, Direction direction, long amountMinor) {
        this.entryId = entryId;
        this.merchantId = merchantId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountMinor = amountMinor;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }
}
