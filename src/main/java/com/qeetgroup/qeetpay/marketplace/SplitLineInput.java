package com.qeetgroup.qeetpay.marketplace;

/**
 * One requested seller line in a split. {@code grossMinor} is the amount attributable to the seller.
 * {@code commissionBps} overrides the seller's default when non-null; the GST/TCS/TDS rates default
 * to the statutory values in {@link SplitCalculator} when null.
 */
public record SplitLineInput(
        String sellerRef,
        long grossMinor,
        Integer commissionBps,
        Integer commissionGstRate,
        Integer tcsBps,
        Integer tdsBps) {}
