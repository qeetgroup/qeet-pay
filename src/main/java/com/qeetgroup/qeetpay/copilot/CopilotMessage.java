package com.qeetgroup.qeetpay.copilot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One append-only turn in a {@link CopilotConversation}. A {@code USER} turn holds the raw question; an
 * {@code ASSISTANT} turn holds the answer narrative plus the cited figures ({@code figuresJson}), the
 * gateway {@code confidence} and {@code fellBack} flag, and {@code aiDecisionId} — the link into the
 * {@code ai.ai_decision} audit row the {@link com.qeetgroup.qeetpay.ai.AiGateway} wrote for this turn.
 * Never mutated after insert.
 */
@Entity
@Table(name = "copilot_messages", schema = "copilot")
public class CopilotMessage {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopilotRole role;

    @Column(nullable = false)
    private String content;

    @Column(name = "figures_json")
    private String figuresJson;

    @Column private Double confidence;

    @Column(name = "fell_back")
    private Boolean fellBack;

    @Column(name = "ai_decision_id")
    private UUID aiDecisionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CopilotMessage() {}

    private CopilotMessage(
            UUID conversationId,
            UUID merchantId,
            CopilotRole role,
            String content,
            String figuresJson,
            Double confidence,
            Boolean fellBack,
            UUID aiDecisionId) {
        this.conversationId = conversationId;
        this.merchantId = merchantId;
        this.role = role;
        this.content = content;
        this.figuresJson = figuresJson;
        this.confidence = confidence;
        this.fellBack = fellBack;
        this.aiDecisionId = aiDecisionId;
    }

    /** A merchant's question turn. */
    public static CopilotMessage user(UUID conversationId, UUID merchantId, String content) {
        return new CopilotMessage(conversationId, merchantId, CopilotRole.USER, content, null, null, null, null);
    }

    /** A copilot answer turn, carrying the cited figures and the gateway outcome. */
    public static CopilotMessage assistant(
            UUID conversationId,
            UUID merchantId,
            String content,
            String figuresJson,
            double confidence,
            boolean fellBack,
            UUID aiDecisionId) {
        return new CopilotMessage(
                conversationId, merchantId, CopilotRole.ASSISTANT, content, figuresJson, confidence, fellBack, aiDecisionId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public CopilotRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getFiguresJson() {
        return figuresJson;
    }

    public Double getConfidence() {
        return confidence;
    }

    public Boolean getFellBack() {
        return fellBack;
    }

    public UUID getAiDecisionId() {
        return aiDecisionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
