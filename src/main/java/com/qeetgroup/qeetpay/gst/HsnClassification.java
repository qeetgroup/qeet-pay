package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Cached HSN/SAC classification for a (merchant, normalised-query) pair (PRD Module 05). The classify
 * path is deterministic, so its result is safely cacheable; a repeat query returns the stored decision
 * (and bumps {@code hitCount}) without a fresh {@link com.qeetgroup.qeetpay.ai.AiGateway} call. No raw
 * description is stored — only {@code queryHash} (SHA-256 of the normalised text) and the result JSON.
 */
@Entity
@Table(name = "hsn_classifications", schema = "gst")
public class HsnClassification {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "query_hash", nullable = false)
    private String queryHash;

    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "primary_hsn_sac", nullable = false)
    private String primaryHsnSac;

    @Column(name = "primary_gst_rate", nullable = false)
    private int primaryGstRate;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "requires_review", nullable = false)
    private boolean requiresReview;

    @Column(name = "ai_decision_id")
    private UUID aiDecisionId;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected HsnClassification() {}

    public HsnClassification(
            UUID merchantId,
            String queryHash,
            String resultJson,
            String primaryHsnSac,
            int primaryGstRate,
            double confidence,
            boolean requiresReview,
            UUID aiDecisionId) {
        this.merchantId = merchantId;
        this.queryHash = queryHash;
        this.resultJson = resultJson;
        this.primaryHsnSac = primaryHsnSac;
        this.primaryGstRate = primaryGstRate;
        this.confidence = confidence;
        this.requiresReview = requiresReview;
        this.aiDecisionId = aiDecisionId;
    }

    /** Records a cache hit — increments the counter and refreshes the last-seen timestamp. */
    public void recordHit() {
        this.hitCount++;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getQueryHash() {
        return queryHash;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getPrimaryHsnSac() {
        return primaryHsnSac;
    }

    public int getPrimaryGstRate() {
        return primaryGstRate;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isRequiresReview() {
        return requiresReview;
    }

    public UUID getAiDecisionId() {
        return aiDecisionId;
    }

    public long getHitCount() {
        return hitCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
