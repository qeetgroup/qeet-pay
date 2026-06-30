package com.qeetgroup.qeetpay.payments;

/** Acceptance method (Phase 1 subset; rails are simulated by the sandbox provider). */
public enum PaymentMethod {
    UPI,
    CARD,
    NET_BANKING,
    WALLET
}
