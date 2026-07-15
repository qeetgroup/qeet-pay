package com.qeetgroup.qeetpay.virtualaccounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An inbound credit to a virtual account, auto-reconciled to the ledger on arrival. Append-only. */
@Entity
@Table(name = "virtual_account_credits", schema = "virtualaccounts")
public class VirtualAccountCredit {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "va_id", nullable = false)
    private UUID vaId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String utr;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_ref")
    private String payerRef;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "credited_at", nullable = false)
    private Instant creditedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected VirtualAccountCredit() {}

    public VirtualAccountCredit(
            UUID vaId, UUID merchantId, long amountMinor, String currency, String utr,
            String payerName, String payerRef, UUID ledgerEntryId) {
        this.vaId = vaId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.utr = utr;
        this.payerName = payerName;
        this.payerRef = payerRef;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() { return id; }
    public UUID getVaId() { return vaId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getUtr() { return utr; }
    public String getPayerName() { return payerName; }
    public String getPayerRef() { return payerRef; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getCreditedAt() { return creditedAt; }
}
