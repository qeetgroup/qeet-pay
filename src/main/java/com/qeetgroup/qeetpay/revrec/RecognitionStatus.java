package com.qeetgroup.qeetpay.revrec;

/** Lifecycle of a recognition schedule: SCHEDULED → IN_PROGRESS → COMPLETED (or CANCELLED). */
public enum RecognitionStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
