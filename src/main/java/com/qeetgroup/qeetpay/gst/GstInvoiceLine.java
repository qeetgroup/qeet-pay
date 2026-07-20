package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One line of a GST invoice with its HSN/SAC, taxable amount, and computed tax (minor units). */
@Entity
@Table(name = "gst_invoice_lines", schema = "gst")
public class GstInvoiceLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String description;

    @Column(name = "hsn_sac", nullable = false)
    private String hsnSac;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "unit_price_minor", nullable = false)
    private long unitPriceMinor;

    @Column(name = "gst_rate", nullable = false)
    private int gstRate;

    @Column(name = "taxable_minor", nullable = false)
    private long taxableMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor;

    @Column(name = "line_total_minor", nullable = false)
    private long lineTotalMinor;

    protected GstInvoiceLine() {}

    public GstInvoiceLine(
            UUID invoiceId,
            UUID merchantId,
            String description,
            String hsnSac,
            long quantity,
            long unitPriceMinor,
            int gstRate,
            long taxableMinor,
            GstCalculator.GstAmounts gst) {
        this.invoiceId = invoiceId;
        this.merchantId = merchantId;
        this.description = description;
        this.hsnSac = hsnSac;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
        this.gstRate = gstRate;
        this.taxableMinor = taxableMinor;
        this.cgstMinor = gst.cgstMinor();
        this.sgstMinor = gst.sgstMinor();
        this.igstMinor = gst.igstMinor();
        this.lineTotalMinor = taxableMinor + gst.totalGstMinor();
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getDescription() {
        return description;
    }

    public String getHsnSac() {
        return hsnSac;
    }

    public int getGstRate() {
        return gstRate;
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

    public long getLineTotalMinor() {
        return lineTotalMinor;
    }
}
