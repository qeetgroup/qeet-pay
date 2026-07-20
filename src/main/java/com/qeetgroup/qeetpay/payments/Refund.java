package com.qeetgroup.qeetpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A (full or partial) refund of a captured payment. Append-only. Amount in minor units. */
@Entity
@Table(name = "refunds", schema = "payments")
public class Refund {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "provider_refund_id")
    private String providerRefundId;

    @Column private String reason;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Refund() {}

    public Refund(
            UUID merchantId,
            UUID paymentId,
            long amountMinor,
            String currency,
            RefundStatus status,
            String providerRefundId,
            String reason,
            UUID ledgerEntryId) {
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = status;
        this.providerRefundId = providerRefundId;
        this.reason = reason;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }
}
