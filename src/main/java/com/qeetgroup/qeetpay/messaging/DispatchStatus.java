package com.qeetgroup.qeetpay.messaging;

/** Delivery state of a dispatched message: QUEUED for the relay → SENT or FAILED on callback. */
public enum DispatchStatus {
    QUEUED,
    SENT,
    FAILED
}
