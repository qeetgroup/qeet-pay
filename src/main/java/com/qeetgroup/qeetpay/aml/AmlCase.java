package com.qeetgroup.qeetpay.aml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An investigation case grouping one or more alerts for an analyst. Opens OPEN; closing records a
 * {@link CaseDisposition} and timestamp. {@code riskScore} is the max score of its attached alerts.
 */
@Entity
@Table(name = "cases", schema = "aml")
public class AmlCase {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String subject;

    @Column private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column
    private CaseDisposition disposition;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected AmlCase() {}

    public AmlCase(UUID merchantId, String subject, String description) {
        this.merchantId = merchantId;
        this.subject = subject;
        this.description = description;
    }

    public void recordAlerts(int alertCount, int maxRiskScore) {
        this.alertCount = alertCount;
        this.riskScore = Math.max(this.riskScore, maxRiskScore);
    }

    public void close(CaseDisposition disposition) {
        if (status == CaseStatus.CLOSED) {
            throw new IllegalStateException("case already closed");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("a disposition is required to close a case");
        }
        this.status = CaseStatus.CLOSED;
        this.disposition = disposition;
        this.closedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public CaseStatus getStatus() { return status; }
    public CaseDisposition getDisposition() { return disposition; }
    public int getRiskScore() { return riskScore; }
    public int getAlertCount() { return alertCount; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
}
