package com.qeetgroup.qeetpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A payment (TAD §6.3, simplified for the acceptance slice). Amount is in integer minor units. */
@Entity
@Table(name = "payments", schema = "payments")
public class Payment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column private String description;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payment() {}

    public Payment(
            UUID merchantId, long amountMinor, String currency, PaymentMethod method, String description) {
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.method = method;
        this.description = description;
        this.provider = "sandbox";
    }

    public void markAuthorized(String providerPaymentId) {
        this.status = PaymentStatus.AUTHORIZED;
        this.providerPaymentId = providerPaymentId;
        touch();
    }

    public void markCaptured(UUID ledgerEntryId) {
        this.status = PaymentStatus.CAPTURED;
        this.ledgerEntryId = ledgerEntryId;
        touch();
    }

    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }
}
