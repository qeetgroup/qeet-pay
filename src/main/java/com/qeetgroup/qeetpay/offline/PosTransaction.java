package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An in-person capture on a POS device, posted money-in to the ledger. Append-only. */
@Entity
@Table(name = "pos_transactions", schema = "offline")
public class PosTransaction {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_method", nullable = false)
    private PosCaptureMethod method;

    /** Retrieval reference number (simulated acquirer RRN). */
    @Column(nullable = false)
    private String rrn;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PosTransaction() {}

    public PosTransaction(
            UUID deviceId, UUID merchantId, long amountMinor, String currency,
            PosCaptureMethod method, String rrn, UUID ledgerEntryId) {
        this.deviceId = deviceId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.method = method;
        this.rrn = rrn;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getMerchantId() { return merchantId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PosCaptureMethod getMethod() { return method; }
    public String getRrn() { return rrn; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getCreatedAt() { return createdAt; }
}
