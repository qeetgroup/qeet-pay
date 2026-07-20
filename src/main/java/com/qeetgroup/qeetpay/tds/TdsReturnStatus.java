package com.qeetgroup.qeetpay.tds;

/** Lifecycle of a TDS/TCS quarterly return: DRAFT/PREPARED (re-preparable) → FILED (immutable). */
public enum TdsReturnStatus {
    DRAFT,
    PREPARED,
    FILED
}
