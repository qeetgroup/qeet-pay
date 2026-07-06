package com.qeetgroup.qeetpay.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single flagged mismatch from a reconciliation run (TAD §6.2). {@code expected} is our ledger
 * view; {@code reported} is what the settlement report stated. Append-only; surfaced for human
 * review. {@code paymentId}/{@code providerPaymentId} are null for batch-level discrepancies.
 */
@Entity
@Table(name = "reconciliation_discrepancies", schema = "reconciliation")
public class Discrepancy {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "reconciliation_id", nullable = false)
    private UUID reconciliationId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyType type;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "expected_minor")
    private Long expectedMinor;

    @Column(name = "reported_minor")
    private Long reportedMinor;

    @Column(nullable = false)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Discrepancy() {}

    public Discrepancy(
            UUID reconciliationId,
            UUID merchantId,
            DiscrepancyType type,
            UUID paymentId,
            String providerPaymentId,
            Long expectedMinor,
            Long reportedMinor,
            String detail) {
        this.reconciliationId = reconciliationId;
        this.merchantId = merchantId;
        this.type = type;
        this.paymentId = paymentId;
        this.providerPaymentId = providerPaymentId;
        this.expectedMinor = expectedMinor;
        this.reportedMinor = reportedMinor;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public Long getExpectedMinor() {
        return expectedMinor;
    }

    public Long getReportedMinor() {
        return reportedMinor;
    }

    public String getDetail() {
        return detail;
    }
}
