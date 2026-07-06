package com.qeetgroup.qeetpay.payouts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A disbursement (TAD Module 02). Amount is in integer minor units. */
@Entity
@Table(name = "payouts", schema = "payouts")
public class Payout {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutRail rail;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status = PayoutStatus.PENDING_APPROVAL;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_payout_id")
    private String providerPayoutId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column private String description;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payout() {}

    public Payout(
            UUID merchantId,
            long amountMinor,
            String currency,
            PayoutRail rail,
            String destination,
            String description) {
        this.merchantId = merchantId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.rail = rail;
        this.destination = destination;
        this.description = description;
        this.provider = "sandbox";
    }

    public void markPaid(String providerPayoutId, UUID ledgerEntryId) {
        this.status = PayoutStatus.PAID;
        this.providerPayoutId = providerPayoutId;
        this.ledgerEntryId = ledgerEntryId;
        touch();
    }

    public void markFailed(String reason) {
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    public void markRejected() {
        this.status = PayoutStatus.REJECTED;
        touch();
    }

    /** Links this payout to a bulk batch (set before persisting; single payouts leave it null). */
    public void assignToBatch(UUID batchId) {
        this.batchId = batchId;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutRail getRail() {
        return rail;
    }

    public String getDestination() {
        return destination;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public UUID getBatchId() {
        return batchId;
    }
}
