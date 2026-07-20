package com.qeetgroup.qeetpay.ondc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An ONDC network order collected by the platform merchant on behalf of one or more parties. Header
 * totals are the sums of the settlement lines. On create the collected gross is held (escrow-like);
 * settlement (post-fulfilment) releases it per party; cancelling records the offsetting reversal.
 */
@Entity
@Table(name = "orders", schema = "ondc")
public class OndcOrder {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "network_order_id", nullable = false)
    private String networkOrderId;

    @Column(name = "buyer_app", nullable = false)
    private String buyerApp;

    @Column(name = "seller_app", nullable = false)
    private String sellerApp;

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

    @Column(name = "party_net_minor", nullable = false)
    private long partyNetMinor;

    @Column(name = "party_count", nullable = false)
    private int partyCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OndcOrderStatus status = OndcOrderStatus.CREATED;

    @Column(name = "hold_entry_id", nullable = false)
    private UUID holdEntryId;

    @Column(name = "settle_entry_id")
    private UUID settleEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected OndcOrder() {}

    public OndcOrder(
            UUID merchantId,
            String networkOrderId,
            String buyerApp,
            String sellerApp,
            String currency,
            long grossMinor,
            long commissionMinor,
            long commissionGstMinor,
            long tcsMinor,
            long partyNetMinor,
            int partyCount,
            UUID holdEntryId) {
        this.merchantId = merchantId;
        this.networkOrderId = networkOrderId;
        this.buyerApp = buyerApp;
        this.sellerApp = sellerApp;
        this.currency = currency;
        this.grossMinor = grossMinor;
        this.commissionMinor = commissionMinor;
        this.commissionGstMinor = commissionGstMinor;
        this.tcsMinor = tcsMinor;
        this.partyNetMinor = partyNetMinor;
        this.partyCount = partyCount;
        this.holdEntryId = holdEntryId;
    }

    public void markFulfilled() {
        if (status != OndcOrderStatus.CREATED) {
            throw new IllegalStateException("only a CREATED order can be fulfilled (was " + status + ")");
        }
        this.status = OndcOrderStatus.FULFILLED;
        this.fulfilledAt = Instant.now();
    }

    public void markSettled(UUID settleEntryId) {
        if (status != OndcOrderStatus.FULFILLED) {
            throw new IllegalStateException("only a FULFILLED order can be settled (was " + status + ")");
        }
        this.status = OndcOrderStatus.SETTLED;
        this.settleEntryId = settleEntryId;
        this.settledAt = Instant.now();
    }

    public void markCancelled(UUID reversalEntryId) {
        if (status == OndcOrderStatus.CANCELLED) {
            throw new IllegalStateException("order already cancelled");
        }
        this.status = OndcOrderStatus.CANCELLED;
        this.reversalEntryId = reversalEntryId;
        this.cancelledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getNetworkOrderId() { return networkOrderId; }
    public String getBuyerApp() { return buyerApp; }
    public String getSellerApp() { return sellerApp; }
    public String getCurrency() { return currency; }
    public long getGrossMinor() { return grossMinor; }
    public long getCommissionMinor() { return commissionMinor; }
    public long getCommissionGstMinor() { return commissionGstMinor; }
    public long getTcsMinor() { return tcsMinor; }
    public long getPartyNetMinor() { return partyNetMinor; }
    public int getPartyCount() { return partyCount; }
    public OndcOrderStatus getStatus() { return status; }
    public UUID getHoldEntryId() { return holdEntryId; }
    public UUID getSettleEntryId() { return settleEntryId; }
    public UUID getReversalEntryId() { return reversalEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
