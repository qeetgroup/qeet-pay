package com.qeetgroup.qeetpay.insurance;

/** Insurance-policy lifecycle. ACTIVE on issue; CANCELLED by the merchant; EXPIRED at term end. */
public enum PolicyStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}
