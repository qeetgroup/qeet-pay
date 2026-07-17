package com.qeetgroup.qeetpay.lending;

/** Lifecycle of a loan offer: OFFERED → ACCEPTED (or EXPIRED / DECLINED). */
public enum LoanOfferStatus {
    OFFERED,
    ACCEPTED,
    EXPIRED,
    DECLINED
}
