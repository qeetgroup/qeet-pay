package com.qeetgroup.qeetpay.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit row for one {@link AiGateway} decision (PRD §6.4 "Auditable"). Records the masked
 * input and its hash (never raw PII), the returned decision + confidence, and how the safety matrix
 * resolved it ({@code humanReviewed}, {@code fellBack}). Never mutated after insert.
 */
@Entity
@Table(name = "ai_decision", schema = "ai")
public class AiDecision {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_hash", nullable = false)
    private String inputHash;

    @Column(name = "masked_input", nullable = false)
    private String maskedInput;

    @Column(name = "output_json", nullable = false)
    private String outputJson;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "human_reviewed", nullable = false)
    private boolean humanReviewed;

    @Column(name = "fell_back", nullable = false)
    private boolean fellBack;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AiDecision() {}

    public AiDecision(
            UUID merchantId,
            String feature,
            String model,
            String inputHash,
            String maskedInput,
            String outputJson,
            double confidence,
            boolean humanReviewed,
            boolean fellBack) {
        this.merchantId = merchantId;
        this.feature = feature;
        this.model = model;
        this.inputHash = inputHash;
        this.maskedInput = maskedInput;
        this.outputJson = outputJson;
        this.confidence = confidence;
        this.humanReviewed = humanReviewed;
        this.fellBack = fellBack;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getFeature() { return feature; }
    public String getModel() { return model; }
    public String getInputHash() { return inputHash; }
    public String getMaskedInput() { return maskedInput; }
    public String getOutputJson() { return outputJson; }
    public double getConfidence() { return confidence; }
    public boolean isHumanReviewed() { return humanReviewed; }
    public boolean isFellBack() { return fellBack; }
    public Instant getCreatedAt() { return createdAt; }
}
