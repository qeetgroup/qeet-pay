package com.qeetgroup.qeetpay.payments;

import java.util.List;
import java.util.UUID;

/**
 * The result of an {@link AiProviderScorer} ranking (PRD Module 07.3): the candidate acquirers ordered
 * best-first, the recommended provider, and how the ranking was produced.
 *
 * @param providers the candidates ranked best-first
 * @param recommendedProvider the top-ranked provider, or null when there are no candidates
 * @param method {@code "ai-predicted"} (model path) or {@code "deterministic-scorecard"} (fallback)
 * @param aiAssisted whether the AI gateway's model path produced the ranking (false = deterministic fallback)
 * @param decisionId the {@code ai.ai_decision} audit-row id for this ranking
 */
public record ProviderRanking(
        List<RankedProvider> providers,
        String recommendedProvider,
        String method,
        boolean aiAssisted,
        UUID decisionId) {}
