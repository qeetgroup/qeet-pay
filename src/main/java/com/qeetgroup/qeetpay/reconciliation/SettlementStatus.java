package com.qeetgroup.qeetpay.reconciliation;

/** Settlement batch lifecycle: ingested, then reconciled clean or flagged with discrepancies. */
public enum SettlementStatus {
    RECEIVED,
    RECONCILED,
    DISCREPANCY
}
