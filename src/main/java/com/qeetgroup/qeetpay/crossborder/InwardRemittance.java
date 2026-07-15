package com.qeetgroup.qeetpay.crossborder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A foreign inward remittance settling an export invoice, with the captured FX rate + FIRA reference. */
@Entity
@Table(name = "inward_remittances", schema = "crossborder")
public class InwardRemittance {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "export_invoice_id", nullable = false)
    private UUID exportInvoiceId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "foreign_amount_minor", nullable = false)
    private long foreignAmountMinor;

    @Column(name = "foreign_currency", nullable = false)
    private String foreignCurrency;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "inr_amount_minor", nullable = false)
    private long inrAmountMinor;

    @Column(name = "fira_reference", nullable = false)
    private String firaReference;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "remitted_at", nullable = false)
    private Instant remittedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected InwardRemittance() {}

    public InwardRemittance(
            UUID exportInvoiceId, UUID merchantId, long foreignAmountMinor, String foreignCurrency,
            BigDecimal fxRate, long inrAmountMinor, String firaReference, String purposeCode,
            UUID ledgerEntryId) {
        this.exportInvoiceId = exportInvoiceId;
        this.merchantId = merchantId;
        this.foreignAmountMinor = foreignAmountMinor;
        this.foreignCurrency = foreignCurrency;
        this.fxRate = fxRate;
        this.inrAmountMinor = inrAmountMinor;
        this.firaReference = firaReference;
        this.purposeCode = purposeCode;
        this.ledgerEntryId = ledgerEntryId;
    }

    public UUID getId() { return id; }
    public UUID getExportInvoiceId() { return exportInvoiceId; }
    public long getForeignAmountMinor() { return foreignAmountMinor; }
    public String getForeignCurrency() { return foreignCurrency; }
    public BigDecimal getFxRate() { return fxRate; }
    public long getInrAmountMinor() { return inrAmountMinor; }
    public String getFiraReference() { return firaReference; }
    public String getPurposeCode() { return purposeCode; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public Instant getRemittedAt() { return remittedAt; }
}
