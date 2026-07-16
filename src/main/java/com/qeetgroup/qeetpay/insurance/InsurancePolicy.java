package com.qeetgroup.qeetpay.insurance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A protection policy a merchant has issued to a payer; its premium is held in the insurance reserve. */
@Entity
@Table(name = "insurance_policies", schema = "insurance")
public class InsurancePolicy {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsuranceProduct product;

    @Column(name = "holder_ref", nullable = false)
    private String holderRef;

    @Column(name = "premium_minor", nullable = false)
    private long premiumMinor;

    @Column(name = "cover_amount_minor", nullable = false)
    private long coverAmountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Column(name = "premium_entry_id", nullable = false)
    private UUID premiumEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected InsurancePolicy() {}

    public InsurancePolicy(
            UUID merchantId, InsuranceProduct product, String holderRef, long premiumMinor,
            long coverAmountMinor, String currency, UUID premiumEntryId) {
        this.merchantId = merchantId;
        this.product = product;
        this.holderRef = holderRef;
        this.premiumMinor = premiumMinor;
        this.coverAmountMinor = coverAmountMinor;
        this.currency = currency;
        this.premiumEntryId = premiumEntryId;
    }

    /** Cancels an active policy; further claims are refused. */
    public void cancel() {
        if (status != PolicyStatus.ACTIVE) {
            throw new IllegalStateException("policy is " + status + ", cannot cancel");
        }
        this.status = PolicyStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public InsuranceProduct getProduct() { return product; }
    public String getHolderRef() { return holderRef; }
    public long getPremiumMinor() { return premiumMinor; }
    public long getCoverAmountMinor() { return coverAmountMinor; }
    public String getCurrency() { return currency; }
    public PolicyStatus getStatus() { return status; }
    public UUID getPremiumEntryId() { return premiumEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
