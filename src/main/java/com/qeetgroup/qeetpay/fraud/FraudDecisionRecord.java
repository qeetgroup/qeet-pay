package com.qeetgroup.qeetpay.fraud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted, merchant-scoped audit row for one fraud-scoring decision (PRD Module 08.1/8.4). Records
 * the score, verdict, the explainable top-contributing features ({@code topReasons}, JSON), the scoring
 * {@code model}, and a link to the {@code ai.ai_decision} row the §6.4 gateway wrote — so the fraud
 * posture is fully auditable. Append-only (SELECT/INSERT under the least-privilege app role).
 */
@Entity
@Table(name = "fraud_decision", schema = "fraud")
public class FraudDecisionRecord {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String decision;

    /** JSON-encoded {@code List<FraudReason>} — the SHAP-style explanation (TAD §8.4). */
    @Column(name = "top_reasons", nullable = false, columnDefinition = "TEXT")
    private String topReasons = "[]";

    @Column(nullable = false)
    private String model;

    @Column(name = "ai_decision_id")
    private UUID aiDecisionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FraudDecisionRecord() {}

    public FraudDecisionRecord(
            UUID merchantId,
            UUID paymentId,
            int score,
            String decision,
            String topReasons,
            String model,
            UUID aiDecisionId) {
        this.merchantId = merchantId;
        this.paymentId = paymentId;
        this.score = score;
        this.decision = decision;
        this.topReasons = topReasons == null ? "[]" : topReasons;
        this.model = model;
        this.aiDecisionId = aiDecisionId;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getPaymentId() { return paymentId; }
    public int getScore() { return score; }
    public String getDecision() { return decision; }
    public String getTopReasons() { return topReasons; }
    public String getModel() { return model; }
    public UUID getAiDecisionId() { return aiDecisionId; }
    public Instant getCreatedAt() { return createdAt; }
}
