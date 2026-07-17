package com.qeetgroup.qeetpay.lending;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A disbursed advance. {@code outstandingMinor} decreases with each swept repayment until REPAID. */
@Entity
@Table(name = "loans", schema = "lending")
public class Loan {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "offer_id", nullable = false)
    private UUID offerId;

    @Column(nullable = false)
    private String currency;

    @Column(name = "principal_minor", nullable = false)
    private long principalMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "total_repayable_minor", nullable = false)
    private long totalRepayableMinor;

    @Column(name = "outstanding_minor", nullable = false)
    private long outstandingMinor;

    @Column(name = "repayment_percent_bps", nullable = false)
    private int repaymentPercentBps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status = LoanStatus.ACTIVE;

    @Column(name = "disbursed_entry_id", nullable = false)
    private UUID disbursedEntryId;

    @Column(name = "disbursed_at", nullable = false)
    private Instant disbursedAt = Instant.now();

    @Column(name = "repaid_at")
    private Instant repaidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Loan() {}

    public Loan(UUID merchantId, LoanOffer offer, UUID disbursedEntryId) {
        this.merchantId = merchantId;
        this.offerId = offer.getId();
        this.currency = offer.getCurrency();
        this.principalMinor = offer.getPrincipalMinor();
        this.feeMinor = offer.getFeeMinor();
        this.totalRepayableMinor = offer.getTotalRepayableMinor();
        this.outstandingMinor = offer.getTotalRepayableMinor();
        this.repaymentPercentBps = offer.getRepaymentPercentBps();
        this.disbursedEntryId = disbursedEntryId;
    }

    /** Applies a swept amount toward the outstanding balance; marks REPAID when it reaches zero. */
    public void applyRepayment(long sweptMinor) {
        if (status != LoanStatus.ACTIVE) {
            throw new IllegalStateException("loan is " + status + ", cannot repay");
        }
        if (sweptMinor <= 0 || sweptMinor > outstandingMinor) {
            throw new IllegalArgumentException("swept amount must be in (0, outstanding]");
        }
        this.outstandingMinor -= sweptMinor;
        this.updatedAt = Instant.now();
        if (outstandingMinor == 0) {
            this.status = LoanStatus.REPAID;
            this.repaidAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getOfferId() { return offerId; }
    public String getCurrency() { return currency; }
    public long getPrincipalMinor() { return principalMinor; }
    public long getFeeMinor() { return feeMinor; }
    public long getTotalRepayableMinor() { return totalRepayableMinor; }
    public long getOutstandingMinor() { return outstandingMinor; }
    public int getRepaymentPercentBps() { return repaymentPercentBps; }
    public LoanStatus getStatus() { return status; }
    public UUID getDisbursedEntryId() { return disbursedEntryId; }
    public Instant getDisbursedAt() { return disbursedAt; }
    public Instant getRepaidAt() { return repaidAt; }
}
