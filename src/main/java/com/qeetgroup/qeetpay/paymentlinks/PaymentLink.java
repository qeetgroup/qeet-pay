package com.qeetgroup.qeetpay.paymentlinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A shareable payment link. {@code amountMinor} null means the payer enters the amount at pay time.
 * Paying it captures a payment and records its id.
 */
@Entity
@Table(name = "payment_links", schema = "paymentlinks")
public class PaymentLink {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Column private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentLinkStatus status = PaymentLinkStatus.ACTIVE;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected PaymentLink() {}

    public PaymentLink(
            UUID merchantId, String code, String title, Long amountMinor, String currency,
            String reference, Instant expiresAt) {
        this.merchantId = merchantId;
        this.code = code;
        this.title = title;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.reference = reference;
        this.expiresAt = expiresAt;
    }

    public boolean isFixedAmount() {
        return amountMinor != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public void markPaid(UUID paymentId) {
        this.status = PaymentLinkStatus.PAID;
        this.paymentId = paymentId;
        this.paidAt = Instant.now();
    }

    public void markExpired() {
        this.status = PaymentLinkStatus.EXPIRED;
    }

    public void cancel() {
        if (status != PaymentLinkStatus.ACTIVE) {
            throw new IllegalStateException("only an ACTIVE link can be cancelled (was " + status + ")");
        }
        this.status = PaymentLinkStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public Long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getReference() { return reference; }
    public PaymentLinkStatus getStatus() { return status; }
    public UUID getPaymentId() { return paymentId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
