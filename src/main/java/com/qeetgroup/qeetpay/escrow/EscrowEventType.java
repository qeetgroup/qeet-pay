package com.qeetgroup.qeetpay.escrow;

/** The kind of escrow movement recorded in the audit trail. */
public enum EscrowEventType {
    HOLD,
    RELEASE,
    REFUND
}
