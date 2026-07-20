package com.qeetgroup.qeetpay.copilot;

import java.util.List;
import java.util.UUID;

/**
 * A copilot answer (PRD Module 12.5 / N7 / 17): the narrative answer, the underlying figures it cites,
 * the data sources used, and the AI-gateway outcome (confidence + whether the deterministic summary
 * was used). Persisted as an {@code ASSISTANT} {@link CopilotMessage} and returned to the caller.
 *
 * @param conversationId the thread this turn belongs to (new or continued)
 * @param messageId the persisted assistant message id
 * @param surface which copilot answered
 * @param question the (raw) question asked
 * @param answer the narrative answer — the model's when confident, else the deterministic summary
 * @param figures the cited underlying figures (always present)
 * @param citations the data sources read to compute the figures (always present)
 * @param confidence gateway confidence in the answer (deterministic fallbacks record {@code 1.0})
 * @param fellBack whether the deterministic summary was used (model error / stale / ambiguous / low
 *     confidence — true whenever the offline sandbox stand-in cannot produce a usable answer)
 * @param requiresHumanReview always false here (copilots are advisory, non-money-affecting)
 * @param aiDecisionId the {@code ai.ai_decision} audit-row id the gateway wrote for this turn
 * @param model the model id the gateway used (the offline stand-in's id when {@code sandbox})
 * @param sandbox whether the offline sandbox model stand-in produced (or would have produced) the result
 */
public record CopilotAnswer(
        UUID conversationId,
        UUID messageId,
        String surface,
        String question,
        String answer,
        List<Figure> figures,
        List<String> citations,
        double confidence,
        boolean fellBack,
        boolean requiresHumanReview,
        UUID aiDecisionId,
        String model,
        boolean sandbox) {}
