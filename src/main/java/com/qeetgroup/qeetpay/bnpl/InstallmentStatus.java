package com.qeetgroup.qeetpay.bnpl;

/** The state of a single scheduled installment: PENDING until the customer repays it, then PAID. */
public enum InstallmentStatus {
    PENDING,
    PAID
}
