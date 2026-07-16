package com.qeetgroup.qeetpay.itc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An INWARD supply — a purchase invoice received from a supplier. Its CGST/SGST/IGST feed the ITC the
 * merchant can claim, once reconciled against the supplier-filed GSTR-2B. Total GST is derived in the
 * constructor; the recon status starts UNMATCHED and is resolved by a 2B run.
 */
@Entity
@Table(name = "purchase_invoices", schema = "itc")
public class PurchaseInvoice {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "supplier_gstin", nullable = false)
    private String supplierGstin;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

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

    @Column(name = "itc_eligible", nullable = false)
    private boolean itcEligible;

    @Enumerated(EnumType.STRING)
    @Column(name = "recon_status", nullable = false)
    private ReconStatus reconStatus = ReconStatus.UNMATCHED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    protected PurchaseInvoice() {}

    public PurchaseInvoice(
            UUID merchantId,
            String supplierGstin,
            String supplierName,
            String invoiceNumber,
            LocalDate invoiceDate,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            boolean itcEligible) {
        this.merchantId = merchantId;
        this.supplierGstin = supplierGstin;
        this.supplierName = supplierName;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.taxableMinor = taxableMinor;
        this.cgstMinor = cgstMinor;
        this.sgstMinor = sgstMinor;
        this.igstMinor = igstMinor;
        this.totalGstMinor = cgstMinor + sgstMinor + igstMinor;
        this.itcEligible = itcEligible;
    }

    /** Records the outcome of a GSTR-2B reconciliation run against this invoice. */
    public void reconcile(ReconStatus status) {
        this.reconStatus = status;
        this.reconciledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSupplierGstin() { return supplierGstin; }
    public String getSupplierName() { return supplierName; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public long getTaxableMinor() { return taxableMinor; }
    public long getCgstMinor() { return cgstMinor; }
    public long getSgstMinor() { return sgstMinor; }
    public long getIgstMinor() { return igstMinor; }
    public long getTotalGstMinor() { return totalGstMinor; }
    public boolean isItcEligible() { return itcEligible; }
    public ReconStatus getReconStatus() { return reconStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReconciledAt() { return reconciledAt; }
}
