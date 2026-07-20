package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A UPI Lite wallet movement (top-up or spend), each posted to the ledger. Append-only. */
@Entity
@Table(name = "upi_lite_txns", schema = "offline")
public class UpiLiteTxn {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private UpiLiteTxnType type;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UpiLiteTxn() {}

    public UpiLiteTxn(
            UUID walletId, UUID merchantId, UpiLiteTxnType type, long amountMinor, String currency,
            UUID ledgerEntryId) {
        this.walletId = walletId;
        this.merchantId = merchantId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public UUID getMerchantId() { return merchantId; }
    public UpiLiteTxnType getType() { return type; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getCreatedAt() { return createdAt; }
}
