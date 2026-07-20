package com.qeetgroup.qeetpay.treasury;

/**
 * What causes a sweep rule to be evaluated on a {@code runSweeps} pass.
 *
 * <ul>
 *   <li>{@link #THRESHOLD} — fires only when the source balance is above {@code thresholdMinor}.
 *   <li>{@link #SCHEDULE} — fires on every scheduled pass (represented by a {@code runSweeps} call),
 *       whenever the source balance still exceeds the {@code keepMinor} buffer.
 * </ul>
 */
public enum SweepTrigger {
    THRESHOLD,
    SCHEDULE
}
