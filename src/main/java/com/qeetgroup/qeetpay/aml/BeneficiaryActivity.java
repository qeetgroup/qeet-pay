package com.qeetgroup.qeetpay.aml;

/**
 * Aggregated activity for a payout beneficiary over an observation window, supplied by the caller as
 * a DTO (the module never reads the {@code payouts} tables). Money is integer minor units (paise).
 *
 * @param beneficiaryRef the beneficiary reference being scored
 * @param inboundMinor total credited into the beneficiary in the window
 * @param outboundMinor total moved back out of the beneficiary in the window
 * @param inboundCount number of distinct inbound transactions (fan-in signal)
 * @param outboundCount number of distinct outbound transactions (fan-out signal)
 * @param medianHoldSeconds median time funds were held before moving out (rapid pass-through signal)
 * @param distinctCounterparties number of distinct counterparties across in + out
 */
public record BeneficiaryActivity(
        String beneficiaryRef,
        long inboundMinor,
        long outboundMinor,
        int inboundCount,
        int outboundCount,
        long medianHoldSeconds,
        int distinctCounterparties) {}
