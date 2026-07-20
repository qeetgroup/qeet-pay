package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A generated Bharat QR — a single unified QR whose payload accepts UPI + RuPay + Visa + Mastercard.
 * A {@code null} amount means a static/open QR (the payer enters the amount); a set amount means a
 * dynamic QR. Generating a QR is not a payment, so it never touches the ledger. Append-only.
 */
@Entity
@Table(name = "bharat_qrs", schema = "offline")
public class BharatQr {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    /** Requested amount in minor units; null for a static/open-amount QR. */
    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "reference", nullable = false)
    private String reference;

    /** The unified QR payload string (EMVCo-style; UPI intent + card-scheme networks). */
    @Column(nullable = false, length = 2048)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BharatQr() {}

    public BharatQr(
            UUID merchantId, String merchantName, Long amountMinor, String currency,
            String reference, String payload) {
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.reference = reference;
        this.payload = payload;
    }

    public boolean isDynamic() {
        return amountMinor != null;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getMerchantName() { return merchantName; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
}
