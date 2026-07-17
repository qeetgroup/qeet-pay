package com.qeetgroup.qeetpay.filing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One outward-supply line of a GSTR-1 return, projected from a GST invoice in the period. */
@Entity
@Table(name = "gst_return_lines", schema = "filing")
public class GstReturnLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "return_id", nullable = false)
    private UUID returnId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "buyer_gstin")
    private String buyerGstin;

    @Column(name = "place_of_supply", nullable = false)
    private String placeOfSupply;

    @Column(name = "supply_type", nullable = false)
    private String supplyType;

    @Column(name = "taxable_minor", nullable = false)
    private long taxableMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected GstReturnLine() {}

    public GstReturnLine(
            UUID returnId,
            UUID merchantId,
            UUID invoiceId,
            String invoiceNumber,
            String buyerGstin,
            String placeOfSupply,
            String supplyType,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor) {
        this.returnId = returnId;
        this.merchantId = merchantId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.buyerGstin = buyerGstin;
        this.placeOfSupply = placeOfSupply;
        this.supplyType = supplyType;
        this.taxableMinor = taxableMinor;
        this.cgstMinor = cgstMinor;
        this.sgstMinor = sgstMinor;
        this.igstMinor = igstMinor;
    }

    public UUID getId() { return id; }
    public UUID getReturnId() { return returnId; }
    public UUID getInvoiceId() { return invoiceId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getBuyerGstin() { return buyerGstin; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public String getSupplyType() { return supplyType; }
    public long getTaxableMinor() { return taxableMinor; }
    public long getCgstMinor() { return cgstMinor; }
    public long getSgstMinor() { return sgstMinor; }
    public long getIgstMinor() { return igstMinor; }
}
