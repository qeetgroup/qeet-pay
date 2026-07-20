package com.qeetgroup.qeetpay.aml;

/** Lifecycle of an AML alert. OPEN on creation; an analyst dismisses or escalates it into a case. */
public enum AlertStatus {
    OPEN,
    DISMISSED,
    ESCALATED
}
