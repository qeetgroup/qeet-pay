package com.qeetgroup.qeetpay.lending;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An underwritten advance offer. Valid until {@code expiresAt}; ACCEPTED exactly once. */
@Entity
@Table(name = "loan_offers", schema = "lending")
public class LoanOffer {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String currency;

    @Column(name = "principal_minor", nullable = false)
    private long principalMinor;

    @Column(name = "fee_bps", nullable = false)
    private int feeBps;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "total_repayable_minor", nullable = false)
    private long totalRepayableMinor;

    @Column(name = "repayment_percent_bps", nullable = false)
    private int repaymentPercentBps;

    @Column(name = "basis_monthly_volume_minor", nullable = false)
    private long basisMonthlyVolumeMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanOfferStatus status = LoanOfferStatus.OFFERED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LoanOffer() {}

    public LoanOffer(
            UUID merchantId, String currency, long principalMinor, int feeBps, long feeMinor,
            int repaymentPercentBps, long basisMonthlyVolumeMinor, Instant expiresAt) {
        this.merchantId = merchantId;
        this.currency = currency;
        this.principalMinor = principalMinor;
        this.feeBps = feeBps;
        this.feeMinor = feeMinor;
        this.totalRepayableMinor = principalMinor + feeMinor;
        this.repaymentPercentBps = repaymentPercentBps;
        this.basisMonthlyVolumeMinor = basisMonthlyVolumeMinor;
        this.expiresAt = expiresAt;
    }

    public void markAccepted() {
        if (status != LoanOfferStatus.OFFERED) {
            throw new IllegalStateException("offer is " + status + ", cannot accept");
        }
        this.status = LoanOfferStatus.ACCEPTED;
        this.updatedAt = Instant.now();
    }

    public void markDeclined() {
        if (status != LoanOfferStatus.OFFERED) {
            throw new IllegalStateException("offer is " + status + ", cannot decline");
        }
        this.status = LoanOfferStatus.DECLINED;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCurrency() { return currency; }
    public long getPrincipalMinor() { return principalMinor; }
    public int getFeeBps() { return feeBps; }
    public long getFeeMinor() { return feeMinor; }
    public long getTotalRepayableMinor() { return totalRepayableMinor; }
    public int getRepaymentPercentBps() { return repaymentPercentBps; }
    public long getBasisMonthlyVolumeMinor() { return basisMonthlyVolumeMinor; }
    public LoanOfferStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
