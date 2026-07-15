package com.qeetgroup.qeetpay.virtualaccounts;

/** A virtual account's state. Only ACTIVE accounts accept inbound credits. */
public enum VirtualAccountStatus {
    ACTIVE,
    CLOSED
}
