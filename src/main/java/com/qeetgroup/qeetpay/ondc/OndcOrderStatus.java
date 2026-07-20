package com.qeetgroup.qeetpay.ondc;

/**
 * An ONDC order's lifecycle. Funds are held on {@code CREATED}; {@code FULFILLED} unlocks
 * settlement; {@code SETTLED} once released per party; {@code CANCELLED} once offset by a reversal.
 */
public enum OndcOrderStatus {
    CREATED,
    FULFILLED,
    SETTLED,
    CANCELLED
}
