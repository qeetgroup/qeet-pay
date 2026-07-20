package com.qeetgroup.qeetpay.tds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One deductee/collectee detail record of a {@link TdsReturn}, projected from a {@link TdsDeduction} in
 * the quarter — the transaction-level annexure rows of an NSDL e-TDS/TCS statement (each carries the
 * deductee PAN, section, amount paid/received and tax deducted/collected).
 */
@Entity
@Table(name = "return_lines", schema = "tds")
public class TdsReturnLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "return_id", nullable = false)
    private UUID returnId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "deduction_id", nullable = false)
    private UUID deductionId;

    @Column(name = "deductee_name", nullable = false)
    private String deducteeName;

    @Column(name = "deductee_pan")
    private String deducteePan;

    @Column(nullable = false)
    private String section;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "deducted_on", nullable = false)
    private LocalDate deductedOn;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TdsReturnLine() {}

    public TdsReturnLine(UUID returnId, UUID merchantId, TdsDeduction d) {
        this.returnId = returnId;
        this.merchantId = merchantId;
        this.deductionId = d.getId();
        this.deducteeName = d.getDeducteeName();
        this.deducteePan = d.getDeducteePan();
        this.section = d.getSection();
        this.grossMinor = d.getGrossMinor();
        this.rateBps = d.getRateBps();
        this.taxMinor = d.getTaxMinor();
        this.deductedOn = d.getDeductedOn();
        this.transactionRef = d.getTransactionRef();
    }

    public UUID getId() { return id; }
    public UUID getReturnId() { return returnId; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getDeductionId() { return deductionId; }
    public String getDeducteeName() { return deducteeName; }
    public String getDeducteePan() { return deducteePan; }
    public String getSection() { return section; }
    public long getGrossMinor() { return grossMinor; }
    public int getRateBps() { return rateBps; }
    public long getTaxMinor() { return taxMinor; }
    public LocalDate getDeductedOn() { return deductedOn; }
    public String getTransactionRef() { return transactionRef; }
    public Instant getCreatedAt() { return createdAt; }
}
