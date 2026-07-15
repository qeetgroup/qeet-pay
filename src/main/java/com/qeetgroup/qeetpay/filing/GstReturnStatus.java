package com.qeetgroup.qeetpay.filing;

/** Lifecycle of a GST return: DRAFT/PREPARED (re-preparable) → FILED (immutable) or ERROR. */
public enum GstReturnStatus {
    DRAFT,
    PREPARED,
    FILED,
    ERROR
}
