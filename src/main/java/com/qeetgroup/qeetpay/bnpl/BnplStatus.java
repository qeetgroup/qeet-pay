package com.qeetgroup.qeetpay.bnpl;

/**
 * BNPL agreement lifecycle. ACTIVE while installments remain outstanding; SETTLED once every
 * installment is paid; CANCELLED if the agreement is voided before completion.
 */
public enum BnplStatus {
    ACTIVE,
    SETTLED,
    CANCELLED
}
