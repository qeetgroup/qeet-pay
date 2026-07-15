package com.qeetgroup.qeetpay.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A split of one collected payment across sellers. Header totals are the sums of the split items;
 * the ledger posting reclassifies the collected gross into commission revenue, tax payable, and
 * seller payables. Cancelling records the offsetting reversal entry.
 */
@Entity
@Table(name = "splits", schema = "marketplace")
public class SplitPayment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(nullable = false)
    private String currency;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    @Column(name = "commission_gst_minor", nullable = false)
    private long commissionGstMinor;

    @Column(name = "tcs_minor", nullable = false)
    private long tcsMinor;

    @Column(name = "tds_minor", nullable = false)
    private long tdsMinor;

    @Column(name = "seller_net_minor", nullable = false)
    private long sellerNetMinor;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitStatus status = SplitStatus.POSTED;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected SplitPayment() {}

    public SplitPayment(
            UUID merchantId,
            UUID paymentId,
            String sourceRef,
            String currency,
            long grossMinor,
            long commissionMinor,
            long commissionGstMinor,
            long tcsMinor,
            long tdsMinor,
            long sellerNetMinor,
            int itemCount,
            UUID ledgerEntryId) {
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.sourceRef = sourceRef;
        this.currency = currency;
        this.grossMinor = grossMinor;
        this.commissionMinor = commissionMinor;
        this.commissionGstMinor = commissionGstMinor;
        this.tcsMinor = tcsMinor;
        this.tdsMinor = tdsMinor;
        this.sellerNetMinor = sellerNetMinor;
        this.itemCount = itemCount;
        this.ledgerEntryId = ledgerEntryId;
    }

    public void markCancelled(UUID reversalEntryId) {
        if (status == SplitStatus.CANCELLED) {
            throw new IllegalStateException("split already cancelled");
        }
        this.status = SplitStatus.CANCELLED;
        this.reversalEntryId = reversalEntryId;
        this.cancelledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getPaymentId() { return paymentId; }
    public String getSourceRef() { return sourceRef; }
    public String getCurrency() { return currency; }
    public long getGrossMinor() { return grossMinor; }
    public long getCommissionMinor() { return commissionMinor; }
    public long getCommissionGstMinor() { return commissionGstMinor; }
    public long getTcsMinor() { return tcsMinor; }
    public long getTdsMinor() { return tdsMinor; }
    public long getSellerNetMinor() { return sellerNetMinor; }
    public int getItemCount() { return itemCount; }
    public SplitStatus getStatus() { return status; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public UUID getReversalEntryId() { return reversalEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
