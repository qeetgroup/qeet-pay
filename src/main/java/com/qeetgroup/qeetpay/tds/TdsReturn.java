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
 * A quarterly TDS/TCS statutory return (PRD Module 06.4). One row per (merchant, form, financial year,
 * quarter): a re-preparable worksheet that aggregates the quarter's {@link TdsDeduction} rows for its
 * {@link TdsReturnForm}, carries the consolidated deposit challan (BSR + serial), and — once filed at
 * the sandbox TIN gateway — records the acknowledgement (provisional receipt number). No money moves
 * here; like {@code filing}, this is a pure compliance worksheet. Merchant-scoped via platform RLS.
 */
@Entity
@Table(name = "returns", schema = "tds")
public class TdsReturn {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TdsReturnForm form;

    /** Assessment financial year, e.g. {@code "2026-27"}. */
    @Column(nullable = false)
    private String fy;

    /** FY quarter label, {@code "Q1".."Q4"} (Q1 = Apr–Jun … Q4 = Jan–Mar). */
    @Column(nullable = false)
    private String quarter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TdsReturnStatus status = TdsReturnStatus.DRAFT;

    @Column(name = "deductee_count", nullable = false)
    private int deducteeCount;

    @Column(name = "deduction_count", nullable = false)
    private int deductionCount;

    @Column(name = "total_gross_minor", nullable = false)
    private long totalGrossMinor;

    /** Total tax deducted/collected across the quarter's rows for this form (paise). */
    @Column(name = "total_tax_minor", nullable = false)
    private long totalTaxMinor;

    @Column(name = "bsr_code")
    private String bsrCode;

    @Column(name = "challan_no")
    private String challanNo;

    @Column(name = "challan_date")
    private LocalDate challanDate;

    /** Provisional receipt number (acknowledgement) assigned on filing. */
    @Column(name = "ack_token")
    private String ackToken;

    @Column(name = "prepared_at")
    private Instant preparedAt;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected TdsReturn() {}

    public TdsReturn(UUID merchantId, TdsReturnForm form, String fy, String quarter) {
        this.merchantId = merchantId;
        this.form = form;
        this.fy = fy;
        this.quarter = quarter;
    }

    /** Recomputes the aggregate totals + deposit challan and marks the return PREPARED (pre-filing only). */
    public void prepare(
            int deducteeCount, int deductionCount, long totalGrossMinor, long totalTaxMinor,
            String bsrCode, String challanNo, LocalDate challanDate) {
        if (status == TdsReturnStatus.FILED) {
            throw new IllegalStateException(form.code() + " " + fy + "/" + quarter + " is already filed");
        }
        this.deducteeCount = deducteeCount;
        this.deductionCount = deductionCount;
        this.totalGrossMinor = totalGrossMinor;
        this.totalTaxMinor = totalTaxMinor;
        this.bsrCode = bsrCode;
        this.challanNo = challanNo;
        this.challanDate = challanDate;
        this.status = TdsReturnStatus.PREPARED;
        this.preparedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Records the acknowledgement token and marks the return FILED (only valid from PREPARED). */
    public void markFiled(String ackToken) {
        if (status != TdsReturnStatus.PREPARED) {
            throw new IllegalStateException("only a PREPARED return can be filed (was " + status + ")");
        }
        if (ackToken == null || ackToken.isBlank()) {
            throw new IllegalArgumentException("acknowledgement token is required");
        }
        this.ackToken = ackToken;
        this.status = TdsReturnStatus.FILED;
        this.filedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public TdsReturnForm getForm() { return form; }
    public String getFy() { return fy; }
    public String getQuarter() { return quarter; }
    public TdsReturnStatus getStatus() { return status; }
    public int getDeducteeCount() { return deducteeCount; }
    public int getDeductionCount() { return deductionCount; }
    public long getTotalGrossMinor() { return totalGrossMinor; }
    public long getTotalTaxMinor() { return totalTaxMinor; }
    public String getBsrCode() { return bsrCode; }
    public String getChallanNo() { return challanNo; }
    public LocalDate getChallanDate() { return challanDate; }
    public String getAckToken() { return ackToken; }
    public Instant getPreparedAt() { return preparedAt; }
    public Instant getFiledAt() { return filedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
