package com.qeetgroup.qeetpay.agentic;

/**
 * The deterministic outcome of an {@code authorize} request against a mandate. {@code allowed} +
 * {@code reason} are the decision; {@code spentMinor}/{@code remainingMinor} reflect the mandate's
 * cumulative-cap state after the (idempotent) evaluation. A denied decision leaves spend unchanged.
 */
public record AuthorizationDecision(
        String mandateId,
        boolean allowed,
        String reason,
        String operation,
        String payeeRef,
        long amountMinor,
        long spentMinor,
        long remainingMinor,
        String useId) {}
