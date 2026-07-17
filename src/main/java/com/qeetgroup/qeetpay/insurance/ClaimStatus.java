package com.qeetgroup.qeetpay.insurance;

/** Claim lifecycle. FILED on submission; then APPROVED/PAID once the payout posts, or REJECTED. */
public enum ClaimStatus {
    FILED,
    APPROVED,
    PAID,
    REJECTED
}
