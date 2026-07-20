package com.qeetgroup.qeetpay.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An in-chat WhatsApp Pay collection request (PRD Module 09.2). CREATED on request; on the confirm
 * callback it either goes PAID (posting the canonical money-in ledger entry, whose id is stored here)
 * or FAILED. Merchant-scoped. Amounts are integer minor units (paise).
 */
@Entity
@Table(name = "whatsapp_pay_collections", schema = "messaging")
public class WhatsAppPayCollection {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payer_phone")
    private String payerPhone;

    @Column(name = "payer_vpa")
    private String payerVpa;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WhatsAppPayStatus status = WhatsAppPayStatus.CREATED;

    @Column private String description;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "related_ref")
    private String relatedRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected WhatsAppPayCollection() {}

    public WhatsAppPayCollection(
            UUID merchantId, String payerPhone, String payerVpa, long amountMinor, String currency,
            String description, String relatedRef) {
        this.merchantId = merchantId;
        this.payerPhone = payerPhone;
        this.payerVpa = payerVpa;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.description = description;
        this.relatedRef = relatedRef;
    }

    public void markPaid(UUID ledgerEntryId, String providerRef) {
        this.status = WhatsAppPayStatus.PAID;
        this.ledgerEntryId = ledgerEntryId;
        this.providerRef = providerRef;
        this.confirmedAt = Instant.now();
    }

    public void markFailed(String providerRef) {
        this.status = WhatsAppPayStatus.FAILED;
        this.providerRef = providerRef;
        this.confirmedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getPayerPhone() { return payerPhone; }
    public String getPayerVpa() { return payerVpa; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public WhatsAppPayStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getProviderRef() { return providerRef; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public String getRelatedRef() { return relatedRef; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
