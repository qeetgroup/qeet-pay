package com.qeetgroup.qeetpay.tds;

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
 * One tax-at-source fact: the tax deducted/collected on a single transaction, under a given section,
 * for a deductee. The certificate number is assigned later (once issued to the deductee); no money
 * moves here.
 */
@Entity
@Table(name = "deductions", schema = "tds")
public class TdsDeduction {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxKind kind;

    @Column(nullable = false)
    private String section;

    @Column(name = "deductee_name", nullable = false)
    private String deducteeName;

    @Column(name = "deductee_pan")
    private String deducteePan;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "deducted_on", nullable = false)
    private LocalDate deductedOn;

    @Column(nullable = false)
    private String quarter;

    @Column(name = "certificate_no")
    private String certificateNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TdsDeduction() {}

    public TdsDeduction(
            UUID merchantId, TaxKind kind, String section, String deducteeName, String deducteePan,
            long grossMinor, int rateBps, long taxMinor, String transactionRef, LocalDate deductedOn,
            String quarter) {
        this.merchantId = merchantId;
        this.kind = kind;
        this.section = section;
        this.deducteeName = deducteeName;
        this.deducteePan = deducteePan;
        this.grossMinor = grossMinor;
        this.rateBps = rateBps;
        this.taxMinor = taxMinor;
        this.transactionRef = transactionRef;
        this.deductedOn = deductedOn;
        this.quarter = quarter;
    }

    /** Assigns the deduction certificate number (issued once, to the deductee). */
    public void issueCertificate(String certificateNo) {
        if (this.certificateNo != null) {
            throw new IllegalStateException("certificate already issued for deduction " + id);
        }
        if (certificateNo == null || certificateNo.isBlank()) {
            throw new IllegalArgumentException("certificate number is required");
        }
        this.certificateNo = certificateNo;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public TaxKind getKind() { return kind; }
    public String getSection() { return section; }
    public String getDeducteeName() { return deducteeName; }
    public String getDeducteePan() { return deducteePan; }
    public long getGrossMinor() { return grossMinor; }
    public int getRateBps() { return rateBps; }
    public long getTaxMinor() { return taxMinor; }
    public String getTransactionRef() { return transactionRef; }
    public LocalDate getDeductedOn() { return deductedOn; }
    public String getQuarter() { return quarter; }
    public String getCertificateNo() { return certificateNo; }
    public Instant getCreatedAt() { return createdAt; }
}
