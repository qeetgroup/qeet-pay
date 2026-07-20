package com.qeetgroup.qeetpay.kyb;

/**
 * V-CIP (Video-based Customer Identification Process) session lifecycle (RBI Master Directions).
 * SCHEDULED → IN_PROGRESS → COMPLETED, or → FAILED from either non-terminal state.
 */
public enum VcipStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
