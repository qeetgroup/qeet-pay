package com.qeetgroup.qeetpay.payouts;

/** Payout lifecycle. Maker-checker: a payout disburses only after approval. */
public enum PayoutStatus {
    PENDING_APPROVAL,
    PAID,
    FAILED,
    REJECTED
}
