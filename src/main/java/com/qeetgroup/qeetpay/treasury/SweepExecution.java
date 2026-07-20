package com.qeetgroup.qeetpay.treasury;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only record of a single sweep that fired for a {@link SweepRule}. */
@Entity
@Table(name = "sweep_executions", schema = "treasury")
public class SweepExecution {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    /** Source balance (minor units) observed just before the sweep posted. */
    @Column(name = "source_balance_before_minor", nullable = false)
    private long sourceBalanceBeforeMinor;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SweepExecution() {}

    public SweepExecution(
            UUID ruleId,
            UUID merchantId,
            long amountMinor,
            long sourceBalanceBeforeMinor,
            UUID ledgerEntryId) {
        this.ruleId = ruleId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.sourceBalanceBeforeMinor = sourceBalanceBeforeMinor;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public long getSourceBalanceBeforeMinor() {
        return sourceBalanceBeforeMinor;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
