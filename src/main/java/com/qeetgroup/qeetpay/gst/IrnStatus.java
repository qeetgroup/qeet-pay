package com.qeetgroup.qeetpay.gst;

/** E-invoice registration state of a GST invoice (TAD §7.3): not yet registered, live, or cancelled. */
public enum IrnStatus {
    NONE,
    GENERATED,
    CANCELLED
}
