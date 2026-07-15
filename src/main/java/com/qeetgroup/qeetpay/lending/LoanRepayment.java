package com.qeetgroup.qeetpay.lending;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One repayment swept from a settlement toward a loan. Append-only. */
@Entity
@Table(name = "loan_repayments", schema = "lending")
public class LoanRepayment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "settlement_amount_minor", nullable = false)
    private long settlementAmountMinor;

    @Column(name = "swept_minor", nullable = false)
    private long sweptMinor;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LoanRepayment() {}

    public LoanRepayment(
            UUID loanId, UUID merchantId, long settlementAmountMinor, long sweptMinor,
            UUID ledgerEntryId, String sourceRef) {
        this.loanId = loanId;
        this.merchantId = merchantId;
        this.settlementAmountMinor = settlementAmountMinor;
        this.sweptMinor = sweptMinor;
        this.ledgerEntryId = ledgerEntryId;
        this.sourceRef = sourceRef;
    }

    public UUID getId() { return id; }
    public UUID getLoanId() { return loanId; }
    public long getSettlementAmountMinor() { return settlementAmountMinor; }
    public long getSweptMinor() { return sweptMinor; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getSourceRef() { return sourceRef; }
    public Instant getCreatedAt() { return createdAt; }
}
