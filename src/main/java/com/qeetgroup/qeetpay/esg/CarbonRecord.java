package com.qeetgroup.qeetpay.esg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only estimate of a single payment's carbon footprint (informational — no ledger impact). */
@Entity
@Table(name = "carbon_records", schema = "esg")
public class CarbonRecord {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CarbonMethod method;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "grams_co2", nullable = false)
    private long gramsCo2;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CarbonRecord() {}

    public CarbonRecord(
            UUID merchantId, String transactionRef, CarbonMethod method, long amountMinor, long gramsCo2) {
        this.merchantId = merchantId;
        this.transactionRef = transactionRef;
        this.method = method;
        this.amountMinor = amountMinor;
        this.gramsCo2 = gramsCo2;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getTransactionRef() { return transactionRef; }
    public CarbonMethod getMethod() { return method; }
    public long getAmountMinor() { return amountMinor; }
    public long getGramsCo2() { return gramsCo2; }
    public Instant getCreatedAt() { return createdAt; }
}
