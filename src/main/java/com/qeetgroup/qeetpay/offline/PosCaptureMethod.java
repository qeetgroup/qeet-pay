package com.qeetgroup.qeetpay.offline;

/** How an in-person POS transaction was captured. */
public enum PosCaptureMethod {
    TAP, // NFC Tap-to-Pay (contactless card / phone)
    SWIPE, // magstripe / chip insert
    QR // scan-to-pay
}
