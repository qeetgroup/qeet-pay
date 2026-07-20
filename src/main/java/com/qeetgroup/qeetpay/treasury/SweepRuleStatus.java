package com.qeetgroup.qeetpay.treasury;

/** A sweep rule is only evaluated while {@link #ACTIVE}; {@link #PAUSED} rules never fire. */
public enum SweepRuleStatus {
    ACTIVE,
    PAUSED
}
