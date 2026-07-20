package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A UPI 123Pay (feature-phone / IVR) payment intent. Created in CREATED, then CONFIRMED (simulated
 * IVR/missed-call completion), at which point money-in is posted to the ledger.
 */
@Entity
@Table(name = "pay123_intents", schema = "offline")
public class Pay123Intent {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payer_mobile", nullable = false)
    private String payerMobile;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Pay123Status status = Pay123Status.CREATED;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected Pay123Intent() {}

    public Pay123Intent(UUID merchantId, String payerMobile, long amountMinor, String currency) {
        this.merchantId = merchantId;
        this.payerMobile = payerMobile;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public void confirm(UUID ledgerEntryId) {
        if (status != Pay123Status.CREATED) {
            throw new IllegalStateException("intent is not confirmable (status " + status + ")");
        }
        this.status = Pay123Status.CONFIRMED;
        this.ledgerEntryId = ledgerEntryId;
        this.confirmedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getPayerMobile() { return payerMobile; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Pay123Status getStatus() { return status; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
