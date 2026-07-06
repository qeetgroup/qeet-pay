package com.qeetgroup.qeetpay.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of a settlement report: which payment settled, its gross, and the PA's fee/tax cut.
 * Append-only. {@code payment_id} is our internal payment (null if the line couldn't be matched);
 * {@code provider_payment_id} is the PA's reference, kept for audit and Phase-2 id mapping.
 */
@Entity
@Table(name = "settlement_items", schema = "reconciliation")
public class SettlementItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "net_minor", nullable = false)
    private long netMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SettlementItem() {}

    public SettlementItem(
            UUID settlementId,
            UUID merchantId,
            UUID paymentId,
            String providerPaymentId,
            long grossMinor,
            long feeMinor,
            long taxMinor,
            long netMinor) {
        this.settlementId = settlementId;
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.providerPaymentId = providerPaymentId;
        this.grossMinor = grossMinor;
        this.feeMinor = feeMinor;
        this.taxMinor = taxMinor;
        this.netMinor = netMinor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public long getGrossMinor() {
        return grossMinor;
    }

    public long getFeeMinor() {
        return feeMinor;
    }

    public long getTaxMinor() {
        return taxMinor;
    }

    public long getNetMinor() {
        return netMinor;
    }
}
