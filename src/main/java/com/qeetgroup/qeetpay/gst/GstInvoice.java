package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A GST-compliant invoice header (TAD §7.2). Amounts in minor units; lines live in GstInvoiceLine. */
@Entity
@Table(name = "gst_invoices", schema = "gst")
public class GstInvoice {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "supplier_gstin", nullable = false)
    private String supplierGstin;

    @Column(name = "buyer_gstin")
    private String buyerGstin;

    @Column(name = "place_of_supply", nullable = false)
    private String placeOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type", nullable = false)
    private SupplyType supplyType;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GstInvoiceStatus status = GstInvoiceStatus.ISSUED;

    @Column(name = "taxable_minor", nullable = false)
    private long taxableMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor;

    @Column(name = "total_gst_minor", nullable = false)
    private long totalGstMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    // ── E-invoicing / IRN (TAD §7.3) ─────────────────────────────────────────

    @Column(name = "irn")
    private String irn;

    @Column(name = "irp_ack_no")
    private String irpAckNo;

    @Column(name = "irp_ack_date")
    private Instant irpAckDate;

    @Column(name = "signed_qr_code")
    private String signedQrCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "irn_status", nullable = false)
    private IrnStatus irnStatus = IrnStatus.NONE;

    @Column(name = "irn_generated_at")
    private Instant irnGeneratedAt;

    @Column(name = "irn_cancelled_at")
    private Instant irnCancelledAt;

    @Column(name = "irn_cancel_reason")
    private String irnCancelReason;

    protected GstInvoice() {}

    public GstInvoice(
            UUID merchantId,
            String invoiceNumber,
            String supplierGstin,
            String buyerGstin,
            String placeOfSupply,
            SupplyType supplyType,
            String currency,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor) {
        this.merchantId = merchantId;
        this.invoiceNumber = invoiceNumber;
        this.supplierGstin = supplierGstin;
        this.buyerGstin = buyerGstin;
        this.placeOfSupply = placeOfSupply;
        this.supplyType = supplyType;
        this.currency = currency;
        this.taxableMinor = taxableMinor;
        this.cgstMinor = cgstMinor;
        this.sgstMinor = sgstMinor;
        this.igstMinor = igstMinor;
        this.totalGstMinor = cgstMinor + sgstMinor + igstMinor;
        this.totalMinor = taxableMinor + this.totalGstMinor;
    }

    public void markPaid(UUID ledgerEntryId) {
        this.status = GstInvoiceStatus.PAID;
        this.ledgerEntryId = ledgerEntryId;
        this.paidAt = Instant.now();
    }

    /** Records the IRP registration result on this invoice (TAD §7.3). */
    public void applyIrn(IrpResult result) {
        if (irnStatus == IrnStatus.GENERATED) {
            throw new IllegalStateException("invoice already has an IRN");
        }
        if (status == GstInvoiceStatus.CANCELLED) {
            throw new IllegalStateException("cannot register a cancelled invoice");
        }
        this.irn = result.irn();
        this.irpAckNo = result.ackNo();
        this.irpAckDate = result.ackDate();
        this.signedQrCode = result.signedQrCode();
        this.irnStatus = IrnStatus.GENERATED;
        this.irnGeneratedAt = Instant.now();
    }

    /** Marks the IRN cancelled at the IRP (within the 24h window), keeping the number for audit. */
    public void cancelIrn(String reason) {
        if (irnStatus != IrnStatus.GENERATED) {
            throw new IllegalStateException("no active IRN to cancel");
        }
        this.irnStatus = IrnStatus.CANCELLED;
        this.irnCancelReason = reason;
        this.irnCancelledAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public SupplyType getSupplyType() {
        return supplyType;
    }

    public String getCurrency() {
        return currency;
    }

    public GstInvoiceStatus getStatus() {
        return status;
    }

    public long getTaxableMinor() {
        return taxableMinor;
    }

    public long getCgstMinor() {
        return cgstMinor;
    }

    public long getSgstMinor() {
        return sgstMinor;
    }

    public long getIgstMinor() {
        return igstMinor;
    }

    public long getTotalGstMinor() {
        return totalGstMinor;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public String getSupplierGstin() {
        return supplierGstin;
    }

    public String getBuyerGstin() {
        return buyerGstin;
    }

    public String getPlaceOfSupply() {
        return placeOfSupply;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public String getIrn() {
        return irn;
    }

    public String getIrpAckNo() {
        return irpAckNo;
    }

    public Instant getIrpAckDate() {
        return irpAckDate;
    }

    public String getSignedQrCode() {
        return signedQrCode;
    }

    public IrnStatus getIrnStatus() {
        return irnStatus;
    }

    public Instant getIrnGeneratedAt() {
        return irnGeneratedAt;
    }

    public Instant getIrnCancelledAt() {
        return irnCancelledAt;
    }

    public String getIrnCancelReason() {
        return irnCancelReason;
    }
}
