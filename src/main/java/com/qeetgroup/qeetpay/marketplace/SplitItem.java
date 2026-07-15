package com.qeetgroup.qeetpay.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One seller's slice of a split, with the full commission/GST/TCS/TDS breakdown retained for audit. */
@Entity
@Table(name = "split_items", schema = "marketplace")
public class SplitItem {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "split_id", nullable = false)
    private UUID splitId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "seller_ref", nullable = false)
    private String sellerRef;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Column(name = "commission_minor", nullable = false)
    private long commissionMinor;

    @Column(name = "commission_gst_rate", nullable = false)
    private int commissionGstRate;

    @Column(name = "commission_gst_minor", nullable = false)
    private long commissionGstMinor;

    @Column(name = "tcs_bps", nullable = false)
    private int tcsBps;

    @Column(name = "tcs_minor", nullable = false)
    private long tcsMinor;

    @Column(name = "tds_bps", nullable = false)
    private int tdsBps;

    @Column(name = "tds_minor", nullable = false)
    private long tdsMinor;

    @Column(name = "net_minor", nullable = false)
    private long netMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SplitItem() {}

    public SplitItem(
            UUID splitId,
            UUID merchantId,
            UUID sellerId,
            String sellerRef,
            int commissionBps,
            int commissionGstRate,
            int tcsBps,
            int tdsBps,
            SplitCalculator.Breakdown b) {
        this.splitId = splitId;
        this.merchantId = merchantId;
        this.sellerId = sellerId;
        this.sellerRef = sellerRef;
        this.commissionBps = commissionBps;
        this.commissionGstRate = commissionGstRate;
        this.tcsBps = tcsBps;
        this.tdsBps = tdsBps;
        this.grossMinor = b.grossMinor();
        this.commissionMinor = b.commissionMinor();
        this.commissionGstMinor = b.commissionGstMinor();
        this.tcsMinor = b.tcsMinor();
        this.tdsMinor = b.tdsMinor();
        this.netMinor = b.netMinor();
    }

    public UUID getId() { return id; }
    public UUID getSplitId() { return splitId; }
    public UUID getSellerId() { return sellerId; }
    public String getSellerRef() { return sellerRef; }
    public long getGrossMinor() { return grossMinor; }
    public int getCommissionBps() { return commissionBps; }
    public long getCommissionMinor() { return commissionMinor; }
    public int getCommissionGstRate() { return commissionGstRate; }
    public long getCommissionGstMinor() { return commissionGstMinor; }
    public int getTcsBps() { return tcsBps; }
    public long getTcsMinor() { return tcsMinor; }
    public int getTdsBps() { return tdsBps; }
    public long getTdsMinor() { return tdsMinor; }
    public long getNetMinor() { return netMinor; }
    public Instant getCreatedAt() { return createdAt; }
}
