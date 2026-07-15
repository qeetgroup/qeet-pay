package com.qeetgroup.qeetpay.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A seller onboarded under a platform (marketplace-operator) merchant. */
@Entity
@Table(name = "sellers", schema = "marketplace")
public class MarketplaceSeller {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "seller_ref", nullable = false)
    private String sellerRef;

    @Column(nullable = false)
    private String name;

    @Column private String gstin;

    @Column private String pan;

    @Column(name = "commission_bps", nullable = false)
    private int commissionBps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status = SellerStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MarketplaceSeller() {}

    public MarketplaceSeller(
            UUID merchantId, String sellerRef, String name, String gstin, String pan, int commissionBps) {
        this.merchantId = merchantId;
        this.sellerRef = sellerRef;
        this.name = name;
        this.gstin = gstin;
        this.pan = pan;
        this.commissionBps = commissionBps;
    }

    public void suspend() { this.status = SellerStatus.SUSPENDED; }
    public void activate() { this.status = SellerStatus.ACTIVE; }
    public boolean isActive() { return status == SellerStatus.ACTIVE; }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSellerRef() { return sellerRef; }
    public String getName() { return name; }
    public String getGstin() { return gstin; }
    public String getPan() { return pan; }
    public int getCommissionBps() { return commissionBps; }
    public SellerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
