package com.qeetgroup.qeetpay.dunning;

import java.util.List;
import java.util.UUID;

/**
 * The AI dunning recommendation (PRD Module 04.2 "Smart Retry" + 04.5 "Explainable Dunning"): for a
 * classified failure and a set of engagement signals, <em>which</em> channels to nudge on and in what
 * order, <em>when</em> to retry (payday-aware), and the <em>tone</em> of the message — each with a
 * plain-English reason.
 *
 * <p>The {@code retryable} flag (the money-affecting stop/continue decision) is always the deterministic
 * {@link FailureClassifier}'s call — never the model's. The channel order / timing / tone are the
 * advisory, personalisable part computed through the AI gateway, with the deterministic heuristic as the
 * fail-closed fallback ({@code aiAssisted == false}).
 *
 * @param category the classified failure category
 * @param retryable whether to auto-retry at all (deterministic; the model never decides this)
 * @param recommendedDelayHours hours to wait before the next retry (payday-aware when signals allow)
 * @param channelOrder ordered notification channels; empty = deliberately silent (e.g. transient errors)
 * @param messageTone suggested message tone, e.g. {@code "empathetic"}, {@code "urgent"}, {@code "formal"}
 * @param reasons plain-English explanation of every part of the plan (the "Explainable Dunning" surface)
 * @param aiAssisted true when the AI gateway's model path produced the plan; false = deterministic fallback
 * @param decisionId the {@code ai.ai_decision} audit-row id for this recommendation
 */
public record AiRetryPlan(
        FailureCategory category,
        boolean retryable,
        int recommendedDelayHours,
        List<String> channelOrder,
        String messageTone,
        List<String> reasons,
        boolean aiAssisted,
        UUID decisionId) {

    /** The channel order as a comma-separated string (the shape the dunning attempt persists). */
    public String channelsCsv() {
        return String.join(",", channelOrder);
    }
}
