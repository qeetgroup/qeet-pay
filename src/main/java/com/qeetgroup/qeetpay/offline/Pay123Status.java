package com.qeetgroup.qeetpay.offline;

/** A UPI 123Pay intent's lifecycle. Only a CREATED intent may be confirmed. */
public enum Pay123Status {
    CREATED,
    CONFIRMED,
    FAILED
}
