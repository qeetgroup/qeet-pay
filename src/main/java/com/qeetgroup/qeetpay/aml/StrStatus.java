package com.qeetgroup.qeetpay.aml;

/** A Suspicious Transaction Report's state. DRAFT until "filed" with FIU-IND, then FILED. */
public enum StrStatus {
    DRAFT,
    FILED
}
