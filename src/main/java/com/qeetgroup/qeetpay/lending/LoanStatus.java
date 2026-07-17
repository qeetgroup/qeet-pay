package com.qeetgroup.qeetpay.lending;

/** Lifecycle of a disbursed advance: ACTIVE → REPAID (or WRITTEN_OFF). */
public enum LoanStatus {
    ACTIVE,
    REPAID,
    WRITTEN_OFF
}
