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
 * An AML alert raised by screening, transaction monitoring, or mule detection. Carries the rule code
 * + category + 0–100 risk score + derived severity. An analyst dismisses it or escalates it by
 * attaching it to an {@link AmlCase}.
 */
@Entity
@Table(name = "alerts", schema = "aml")
public class AmlAlert {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subject_ref", nullable = false)
    private String subjectRef;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private String category;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    /** JSON object with the rule-specific detail. */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AmlAlert() {}

    public AmlAlert(
            UUID merchantId,
            String subjectRef,
            String transactionRef,
            String ruleCode,
            String category,
            int riskScore,
            String detail) {
        this.merchantId = merchantId;
        this.subjectRef = subjectRef;
        this.transactionRef = transactionRef;
        this.ruleCode = ruleCode;
        this.category = category;
        this.riskScore = riskScore;
        this.severity = AlertSeverity.fromScore(riskScore);
        this.detail = detail;
    }

    public void attachToCase(UUID caseId) {
        this.caseId = caseId;
        this.status = AlertStatus.ESCALATED;
    }

    public void dismiss() {
        this.status = AlertStatus.DISMISSED;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSubjectRef() { return subjectRef; }
    public String getTransactionRef() { return transactionRef; }
    public String getRuleCode() { return ruleCode; }
    public String getCategory() { return category; }
    public int getRiskScore() { return riskScore; }
    public AlertSeverity getSeverity() { return severity; }
    public String getDetail() { return detail; }
    public AlertStatus getStatus() { return status; }
    public UUID getCaseId() { return caseId; }
    public Instant getCreatedAt() { return createdAt; }
}
