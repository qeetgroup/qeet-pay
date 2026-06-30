package com.qeetgroup.qeetpay.payments;

/** Payment lifecycle (TAD §4.1). */
public enum PaymentStatus {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED
}
