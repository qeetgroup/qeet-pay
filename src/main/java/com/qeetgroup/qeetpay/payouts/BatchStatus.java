package com.qeetgroup.qeetpay.payouts;

/** Bulk-payout batch lifecycle. Maker-checker: a batch disburses only once approved as a unit. */
public enum BatchStatus {
    PENDING_APPROVAL,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    REJECTED
}
