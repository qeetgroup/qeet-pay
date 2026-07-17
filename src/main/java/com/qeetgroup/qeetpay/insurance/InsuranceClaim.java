package com.qeetgroup.qeetpay.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A claim filed against a policy's cover, then paid out of the reserve or rejected. */
@Entity
@Table(name = "insurance_claims", schema = "insurance")
public class InsuranceClaim {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.FILED;

    @Column(name = "payout_entry_id")
    private UUID payoutEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected InsuranceClaim() {}

    public InsuranceClaim(UUID policyId, UUID merchantId, long amountMinor, String reason) {
        this.policyId = policyId;
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.reason = reason;
    }

    /** Approves a filed claim and records its payout posting (FILED → PAID). */
    public void approveAndPay(UUID payoutEntryId) {
        requireFiled();
        this.status = ClaimStatus.PAID;
        this.payoutEntryId = payoutEntryId;
        this.decidedAt = Instant.now();
    }

    /** Rejects a filed claim (FILED → REJECTED); no money moves. */
    public void reject() {
        requireFiled();
        this.status = ClaimStatus.REJECTED;
        this.decidedAt = Instant.now();
    }

    private void requireFiled() {
        if (status != ClaimStatus.FILED) {
            throw new IllegalStateException("claim is " + status + ", not open for a decision");
        }
    }

    public UUID getId() { return id; }
    public UUID getPolicyId() { return policyId; }
    public UUID getMerchantId() { return merchantId; }
    public long getAmountMinor() { return amountMinor; }
    public String getReason() { return reason; }
    public ClaimStatus getStatus() { return status; }
    public UUID getPayoutEntryId() { return payoutEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}
