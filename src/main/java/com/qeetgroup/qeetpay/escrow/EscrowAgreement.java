package com.qeetgroup.qeetpay.escrow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An escrow hold between a buyer and a seller, released/refunded (fully or partially) over time. */
@Entity
@Table(name = "escrow_agreements", schema = "escrow")
public class EscrowAgreement {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "buyer_ref", nullable = false)
    private String buyerRef;

    @Column(name = "seller_ref", nullable = false)
    private String sellerRef;

    @Column(nullable = false)
    private String currency;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "released_minor", nullable = false)
    private long releasedMinor = 0;

    @Column(name = "refunded_minor", nullable = false)
    private long refundedMinor = 0;

    @Column private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowStatus status = EscrowStatus.HELD;

    @Column(name = "hold_entry_id", nullable = false)
    private UUID holdEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected EscrowAgreement() {}

    public EscrowAgreement(
            UUID merchantId, String buyerRef, String sellerRef, String currency,
            long amountMinor, String description, UUID holdEntryId) {
        this.merchantId = merchantId;
        this.buyerRef = buyerRef;
        this.sellerRef = sellerRef;
        this.currency = currency;
        this.amountMinor = amountMinor;
        this.description = description;
        this.holdEntryId = holdEntryId;
    }

    public long remainingMinor() {
        return amountMinor - releasedMinor - refundedMinor;
    }

    /** Records a release to the seller; recomputes status. */
    public void applyRelease(long amountMinor) {
        requireAllocatable(amountMinor);
        this.releasedMinor += amountMinor;
        recomputeStatus();
    }

    /** Records a refund to the buyer; recomputes status. */
    public void applyRefund(long amountMinor) {
        requireAllocatable(amountMinor);
        this.refundedMinor += amountMinor;
        recomputeStatus();
    }

    private void requireAllocatable(long amountMinor) {
        if (status != EscrowStatus.HELD) {
            throw new IllegalStateException("escrow is fully allocated (" + status + ")");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amountMinor > remainingMinor()) {
            throw new IllegalArgumentException(
                    "amount " + amountMinor + " exceeds escrow remaining " + remainingMinor());
        }
    }

    private void recomputeStatus() {
        this.updatedAt = Instant.now();
        if (releasedMinor + refundedMinor < amountMinor) {
            this.status = EscrowStatus.HELD;
            return;
        }
        if (refundedMinor == 0) {
            this.status = EscrowStatus.RELEASED;
        } else if (releasedMinor == 0) {
            this.status = EscrowStatus.REFUNDED;
        } else {
            this.status = EscrowStatus.SETTLED;
        }
        this.closedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getBuyerRef() { return buyerRef; }
    public String getSellerRef() { return sellerRef; }
    public String getCurrency() { return currency; }
    public long getAmountMinor() { return amountMinor; }
    public long getReleasedMinor() { return releasedMinor; }
    public long getRefundedMinor() { return refundedMinor; }
    public String getDescription() { return description; }
    public EscrowStatus getStatus() { return status; }
    public UUID getHoldEntryId() { return holdEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
