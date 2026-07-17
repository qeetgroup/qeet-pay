package com.qeetgroup.qeetpay.filing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A GST return for one tax period. Totals are the sum of the period's invoices; the ARN is set on filing. */
@Entity
@Table(name = "gst_returns", schema = "filing")
public class GstReturn {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false)
    private GstReturnType returnType;

    @Column(nullable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GstReturnStatus status = GstReturnStatus.DRAFT;

    @Column(name = "invoice_count", nullable = false)
    private int invoiceCount;

    @Column(name = "total_taxable_minor", nullable = false)
    private long totalTaxableMinor;

    @Column(name = "total_cgst_minor", nullable = false)
    private long totalCgstMinor;

    @Column(name = "total_sgst_minor", nullable = false)
    private long totalSgstMinor;

    @Column(name = "total_igst_minor", nullable = false)
    private long totalIgstMinor;

    @Column(name = "total_tax_minor", nullable = false)
    private long totalTaxMinor;

    @Column(name = "gstn_arn")
    private String gstnArn;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected GstReturn() {}

    public GstReturn(UUID merchantId, GstReturnType returnType, String period) {
        this.merchantId = merchantId;
        this.returnType = returnType;
        this.period = period;
    }

    /** Recomputes the aggregate totals and marks the return PREPARED (only valid pre-filing). */
    public void prepare(int invoiceCount, long taxable, long cgst, long sgst, long igst) {
        if (status == GstReturnStatus.FILED) {
            throw new IllegalStateException("return already filed for " + period);
        }
        this.invoiceCount = invoiceCount;
        this.totalTaxableMinor = taxable;
        this.totalCgstMinor = cgst;
        this.totalSgstMinor = sgst;
        this.totalIgstMinor = igst;
        this.totalTaxMinor = cgst + sgst + igst;
        this.status = GstReturnStatus.PREPARED;
        this.preparedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFiled(String arn) {
        if (status != GstReturnStatus.PREPARED) {
            throw new IllegalStateException("only a PREPARED return can be filed (was " + status + ")");
        }
        this.gstnArn = arn;
        this.status = GstReturnStatus.FILED;
        this.filedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public GstReturnType getReturnType() { return returnType; }
    public String getPeriod() { return period; }
    public GstReturnStatus getStatus() { return status; }
    public int getInvoiceCount() { return invoiceCount; }
    public long getTotalTaxableMinor() { return totalTaxableMinor; }
    public long getTotalCgstMinor() { return totalCgstMinor; }
    public long getTotalSgstMinor() { return totalSgstMinor; }
    public long getTotalIgstMinor() { return totalIgstMinor; }
    public long getTotalTaxMinor() { return totalTaxMinor; }
    public String getGstnArn() { return gstnArn; }
    public Instant getPreparedAt() { return preparedAt; }
    public Instant getFiledAt() { return filedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
