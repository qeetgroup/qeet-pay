package com.qeetgroup.qeetpay.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A provider settlement batch (TAD §6.2): the PA disbursed net funds to the merchant's nodal/bank
 * account. {@code gross = fee + tax + net}. Idempotent per (merchant, provider, provider id).
 * Mutates (RECEIVED → RECONCILED | DISCREPANCY) as reconciliation runs.
 */
@Entity
@Table(name = "settlements", schema = "reconciliation")
public class Settlement {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_settlement_id", nullable = false)
    private String providerSettlementId;

    @Column(nullable = false)
    private String currency;

    @Column(name = "gross_amount_minor", nullable = false)
    private long grossAmountMinor;

    @Column(name = "fee_amount_minor", nullable = false)
    private long feeAmountMinor;

    @Column(name = "tax_amount_minor", nullable = false)
    private long taxAmountMinor;

    @Column(name = "net_amount_minor", nullable = false)
    private long netAmountMinor;

    @Column(name = "reported_net_minor")
    private Long reportedNetMinor;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status = SettlementStatus.RECEIVED;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Settlement() {}

    public Settlement(
            UUID merchantId,
            String provider,
            String providerSettlementId,
            String currency,
            long grossAmountMinor,
            long feeAmountMinor,
            long taxAmountMinor,
            long netAmountMinor,
            Long reportedNetMinor,
            int itemCount,
            Instant settledAt) {
        this.merchantId = merchantId;
        this.provider = provider;
        this.providerSettlementId = providerSettlementId;
        this.currency = currency;
        this.grossAmountMinor = grossAmountMinor;
        this.feeAmountMinor = feeAmountMinor;
        this.taxAmountMinor = taxAmountMinor;
        this.netAmountMinor = netAmountMinor;
        this.reportedNetMinor = reportedNetMinor;
        this.itemCount = itemCount;
        this.settledAt = settledAt != null ? settledAt : Instant.now();
    }

    /** Records the ledger posting that moved the funds (settlement → bank, less fees). */
    public void recordPosting(UUID ledgerEntryId) {
        this.ledgerEntryId = ledgerEntryId;
        touch();
    }

    public void markReconciled() {
        this.status = SettlementStatus.RECONCILED;
        touch();
    }

    public void markDiscrepancy() {
        this.status = SettlementStatus.DISCREPANCY;
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

    public String getProvider() {
        return provider;
    }

    public String getProviderSettlementId() {
        return providerSettlementId;
    }

    public String getCurrency() {
        return currency;
    }

    public long getGrossAmountMinor() {
        return grossAmountMinor;
    }

    public long getFeeAmountMinor() {
        return feeAmountMinor;
    }

    public long getTaxAmountMinor() {
        return taxAmountMinor;
    }

    public long getNetAmountMinor() {
        return netAmountMinor;
    }

    public Long getReportedNetMinor() {
        return reportedNetMinor;
    }

    public int getItemCount() {
        return itemCount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }
}
