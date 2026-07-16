package com.qeetgroup.qeetpay.itc;

/**
 * Reconciliation state of a purchase invoice against supplier-filed GSTR-2B data. Starts
 * {@code UNMATCHED}; a 2B run resolves it to MATCHED (GST agrees), MISMATCHED (GST differs), or
 * MISSING_IN_2B (no line the supplier filed).
 */
public enum ReconStatus {
    UNMATCHED,
    MATCHED,
    MISMATCHED,
    MISSING_IN_2B
}
