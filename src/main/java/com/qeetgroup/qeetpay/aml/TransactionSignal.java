package com.qeetgroup.qeetpay.aml;

/**
 * The inputs the transaction-monitoring rules engine evaluates for one transaction. Supplied by the
 * caller (or an upstream module) — this module never reads the payments/payouts tables directly.
 * Money is integer minor units (paise).
 *
 * @param transactionRef caller's reference for the transaction
 * @param amountMinor the transaction amount in minor units (paise); must be positive
 * @param currency ISO currency code
 * @param mcc merchant category code, if known (for the high-risk-MCC rule)
 * @param countryCode ISO-3166 alpha-2 country of the counterparty, if known (geo rule)
 * @param txnCount24h the party's transaction count in the trailing 24h, if known (velocity rule)
 * @param amount24hMinor the party's cumulative amount in the trailing 24h, if known (velocity rule)
 * @param beneficiaryRef the payout beneficiary / counterparty reference, if any
 */
public record TransactionSignal(
        String transactionRef,
        long amountMinor,
        String currency,
        Integer mcc,
        String countryCode,
        Integer txnCount24h,
        Long amount24hMinor,
        String beneficiaryRef) {}
