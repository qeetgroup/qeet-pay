package com.qeetgroup.qeetpay.accounting;

/** Lifecycle of one {@link AccountingSync} run. */
public enum SyncStatus {
    PENDING,
    SUCCESS,
    FAILED
}
