package com.qeetgroup.qeetpay.payments;

/**
 * Rolling health signal for an acquirer (PRD Module 07.3). Driven by consecutive failures: a run of
 * failures degrades then trips the provider to DOWN (routed around); a success restores it.
 */
public enum ProviderHealth {
    HEALTHY,
    DEGRADED,
    DOWN
}
