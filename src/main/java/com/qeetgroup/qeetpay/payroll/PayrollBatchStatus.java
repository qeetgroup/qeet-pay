package com.qeetgroup.qeetpay.payroll;

/**
 * Payroll batch lifecycle. Maker-checker: a batch disburses only after approval. A run can finish
 * partially executed (PRD Module 02.5 edge case) when some lines' rails fail.
 */
public enum PayrollBatchStatus {
    PENDING_APPROVAL,
    DISBURSED,
    PARTIALLY_DISBURSED,
    FAILED,
    REJECTED
}
