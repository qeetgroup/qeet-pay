package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * GST Credit Note or Debit Note — a formal correction to an issued GST invoice.
 * Once ISSUED, a note can be APPLIED (ledger offset posted) or CANCELLED.
 */
@Entity
@Table(name = "gst_notes", schema = "gst")
public class GstNote {

    public static final String CREDIT_NOTE = "CREDIT_NOTE";
    public static final String DEBIT_NOTE  = "DEBIT_NOTE";
    public static final String ISSUED    = "ISSUED";
    public static final String APPLIED   = "APPLIED";
    public static final String CANCELLED = "CANCELLED";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String type;

    @Column(name = "original_invoice_id", nullable = false)
    private UUID originalInvoiceId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "taxable_minor", nullable = false)
    private long taxableMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor = 0L;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor = 0L;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor = 0L;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(nullable = false)
    private String status = ISSUED;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    protected GstNote() {}

    public GstNote(UUID merchantId, String type, UUID originalInvoiceId, String reason,
            long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor) {
        this.merchantId = merchantId;
        this.type = type;
        this.originalInvoiceId = originalInvoiceId;
        this.reason = reason;
        this.taxableMinor = taxableMinor;
        this.cgstMinor = cgstMinor;
        this.sgstMinor = sgstMinor;
        this.igstMinor = igstMinor;
        this.totalMinor = taxableMinor + cgstMinor + sgstMinor + igstMinor;
    }

    public void apply(UUID ledgerEntryId) {
        if (!ISSUED.equals(status)) throw new IllegalStateException("note is not in ISSUED status; status=" + status);
        this.status = APPLIED;
        this.ledgerEntryId = ledgerEntryId;
    }

    public void cancel() {
        if (APPLIED.equals(status)) throw new IllegalStateException("cannot cancel an APPLIED note");
        this.status = CANCELLED;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getType() { return type; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public String getReason() { return reason; }
    public long getTaxableMinor() { return taxableMinor; }
    public long getCgstMinor() { return cgstMinor; }
    public long getSgstMinor() { return sgstMinor; }
    public long getIgstMinor() { return igstMinor; }
    public long getTotalMinor() { return totalMinor; }
    public String getStatus() { return status; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getIssuedAt() { return issuedAt; }
}
