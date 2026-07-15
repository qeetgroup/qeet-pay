package com.qeetgroup.qeetpay.dunning;

/**
 * The dunning classifier's decision for a failed collection (PRD Module 04.1): what kind of failure
 * it was, whether to auto-retry, how long to wait, which channels to nudge on, and a plain-English
 * {@code rationale} (the "explainable" part — surfaced to the merchant, not just a black-box score).
 */
public record RetryRecommendation(
        FailureCategory category,
        boolean retryable,
        int recommendedDelayHours,
        String recommendedChannels,
        String rationale) {}
