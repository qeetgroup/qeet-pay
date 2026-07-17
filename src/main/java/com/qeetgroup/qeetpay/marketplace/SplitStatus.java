package com.qeetgroup.qeetpay.marketplace;

/** A split's state. POSTED once the ledger entry is written; CANCELLED once offset by a reversal. */
public enum SplitStatus {
    POSTED,
    CANCELLED
}
