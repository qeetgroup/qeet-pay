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
 * One party's slice of an ONDC order settlement, with the full commission/GST/TCS breakdown retained
 * for GSTR-8 / settlement audit. Append-only.
 */
@Entity
@Table(name = "settlement_lines", schema = "ondc")
public class OndcSettlementLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "party_ref", nullable = false)
    private String partyRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false)
    private PartyRole partyRole;

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

    @Column(name = "net_minor", nullable = false)
    private long netMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected OndcSettlementLine() {}

    public OndcSettlementLine(
            UUID orderId,
            UUID merchantId,
            String partyRef,
            PartyRole partyRole,
            int commissionBps,
            int commissionGstRate,
            int tcsBps,
            OndcSplitCalculator.Breakdown b) {
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.partyRef = partyRef;
        this.partyRole = partyRole;
        this.commissionBps = commissionBps;
        this.commissionGstRate = commissionGstRate;
        this.tcsBps = tcsBps;
        this.grossMinor = b.grossMinor();
        this.commissionMinor = b.commissionMinor();
        this.commissionGstMinor = b.commissionGstMinor();
        this.tcsMinor = b.tcsMinor();
        this.netMinor = b.netMinor();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getMerchantId() { return merchantId; }
    public String getPartyRef() { return partyRef; }
    public PartyRole getPartyRole() { return partyRole; }
    public long getGrossMinor() { return grossMinor; }
    public int getCommissionBps() { return commissionBps; }
    public long getCommissionMinor() { return commissionMinor; }
    public int getCommissionGstRate() { return commissionGstRate; }
    public long getCommissionGstMinor() { return commissionGstMinor; }
    public int getTcsBps() { return tcsBps; }
    public long getTcsMinor() { return tcsMinor; }
    public long getNetMinor() { return netMinor; }
    public Instant getCreatedAt() { return createdAt; }
}
