package com.qeetgroup.qeetpay.crossborder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A foreign-currency export invoice (zero-rated under LUT), settled by a foreign inward remittance. */
@Entity
@Table(name = "export_invoices", schema = "crossborder")
public class ExportInvoice {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "buyer_country", nullable = false)
    private String buyerCountry;

    @Column(nullable = false)
    private String currency;

    @Column(name = "foreign_amount_minor", nullable = false)
    private long foreignAmountMinor;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(nullable = false)
    private boolean lut = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportInvoiceStatus status = ExportInvoiceStatus.ISSUED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ExportInvoice() {}

    public ExportInvoice(
            UUID merchantId, String invoiceNumber, String buyerCountry, String currency,
            long foreignAmountMinor, String purposeCode, boolean lut) {
        this.merchantId = merchantId;
        this.invoiceNumber = invoiceNumber;
        this.buyerCountry = buyerCountry;
        this.currency = currency;
        this.foreignAmountMinor = foreignAmountMinor;
        this.purposeCode = purposeCode;
        this.lut = lut;
    }

    public void markRemitted() {
        this.status = ExportInvoiceStatus.REMITTED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getBuyerCountry() { return buyerCountry; }
    public String getCurrency() { return currency; }
    public long getForeignAmountMinor() { return foreignAmountMinor; }
    public String getPurposeCode() { return purposeCode; }
    public boolean isLut() { return lut; }
    public ExportInvoiceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
