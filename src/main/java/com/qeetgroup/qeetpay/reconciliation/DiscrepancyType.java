package com.qeetgroup.qeetpay.reconciliation;

/**
 * The kinds of mismatch a reconciliation run can flag (TAD §6.2): per-line ledger-vs-report
 * differences, plus two batch-level invariants (control total and the nodal-account balance).
 */
public enum DiscrepancyType {
    /** The report references a payment we have no record of. */
    MISSING_IN_LEDGER,
    /** The payment exists but was never captured. */
    STATUS_NOT_CAPTURED,
    /** The captured amount does not equal the settled gross. */
    AMOUNT_MISMATCH,
    /** The same payment was settled in more than one batch. */
    DUPLICATE_SETTLEMENT,
    /** The sum of line nets does not equal the report's stated net control total. */
    BATCH_TOTAL_MISMATCH,
    /** The settlement holding account went negative — settled out more than was held. */
    NODAL_IMBALANCE
}
