package com.qeetgroup.qeetpay.gst;

/** Place-of-supply classification (TAD §7.2). Intra-state splits CGST+SGST; inter-state uses IGST. */
public enum SupplyType {
    INTRA_STATE,
    INTER_STATE
}
