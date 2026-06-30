package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Per-merchant, per-fiscal-year sequence for GST invoice numbering (locked on increment). */
@Entity
@Table(name = "invoice_counters", schema = "gst")
public class InvoiceCounter {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "fiscal_year", nullable = false)
    private String fiscalYear;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq = 0;

    protected InvoiceCounter() {}

    public InvoiceCounter(UUID merchantId, String fiscalYear) {
        this.merchantId = merchantId;
        this.fiscalYear = fiscalYear;
    }

    public long next() {
        return ++lastSeq;
    }

    public String getFiscalYear() {
        return fiscalYear;
    }
}
