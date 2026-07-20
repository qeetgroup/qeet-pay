package com.qeetgroup.qeetpay.offline;

/** A UPI Lite wallet's state. Only ACTIVE wallets accept top-ups and spends. */
public enum UpiLiteWalletStatus {
    ACTIVE,
    CLOSED
}
