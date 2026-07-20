package com.qeetgroup.qeetpay.agentic;

/**
 * Lifecycle of an agent mandate. ACTIVE while it can authorize actions; REVOKED once the merchant
 * cancels it; EXPIRED once past its {@code expires_at}. Only ACTIVE, in-window mandates authorize.
 */
public enum AgentMandateStatus {
    ACTIVE,
    REVOKED,
    EXPIRED
}
