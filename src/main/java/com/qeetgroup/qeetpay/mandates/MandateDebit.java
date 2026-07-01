package com.qeetgroup.qeetpay.mandates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable record of a single debit executed against a mandate (TAD Module 02). */
@Entity
@Table(name = "mandate_debits", schema = "mandates")
public class MandateDebit {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "mandate_id", nullable = false)
    private UUID mandateId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status; // SUCCEEDED | FAILED

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MandateDebit() {}

    public MandateDebit(
            UUID mandateId, UUID merchantId, long amountMinor, String currency,
            String status, UUID paymentId, String failureReason, UUID ledgerEntryId) {
        this.mandateId = mandateId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.paymentId = paymentId;
        this.failureReason = failureReason;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() { return id; }
    public UUID getMandateId() { return mandateId; }
    public UUID getMerchantId() { return merchantId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public UUID getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
}
