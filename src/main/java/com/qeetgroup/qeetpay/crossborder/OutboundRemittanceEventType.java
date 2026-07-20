package com.qeetgroup.qeetpay.crossborder;

/** Append-only outbound-remittance transitions: funds debited, wire settled, or wire rejected. */
public enum OutboundRemittanceEventType {
    CREATED,
    REMITTED,
    FAILED
}
