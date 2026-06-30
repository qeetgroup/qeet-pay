package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An invoice for a subscription period. Paid invoices carry the ledger entry that recognised them. */
@Entity
@Table(name = "invoices", schema = "billing")
public class Invoice {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Invoice() {}

    public Invoice(UUID merchantId, UUID subscriptionId, long amountMinor, String currency) {
        this.merchantId = merchantId;
        this.subscriptionId = subscriptionId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public void markPaid(UUID ledgerEntryId) {
        this.status = InvoiceStatus.PAID;
        this.ledgerEntryId = ledgerEntryId;
        this.paidAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }
}
