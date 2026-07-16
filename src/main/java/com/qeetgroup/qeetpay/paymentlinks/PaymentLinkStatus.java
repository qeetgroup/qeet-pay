package com.qeetgroup.qeetpay.paymentlinks;

/** Payment-link lifecycle: ACTIVE until paid (PAID), expired (EXPIRED), or revoked (CANCELLED). */
public enum PaymentLinkStatus {
    ACTIVE,
    PAID,
    EXPIRED,
    CANCELLED
}
