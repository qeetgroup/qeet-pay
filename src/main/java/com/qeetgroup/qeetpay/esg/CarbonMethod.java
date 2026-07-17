package com.qeetgroup.qeetpay.esg;

/**
 * Acceptance method a footprint estimate is keyed on. A local copy of the payment rails (mirrors
 * {@code payments.PaymentMethod}) so the ESG module carries no dependency on the payments module.
 */
public enum CarbonMethod {
    UPI,
    CARD,
    NET_BANKING,
    WALLET
}
