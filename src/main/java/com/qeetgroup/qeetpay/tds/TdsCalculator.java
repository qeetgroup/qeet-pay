package com.qeetgroup.qeetpay.tds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * TDS/TCS arithmetic and period helpers (PRD Module 06). Pure + deterministic — no Spring/DB — so it
 * is unit-testable in isolation like {@link com.qeetgroup.qeetpay.marketplace.SplitCalculator}. All
 * money is integer minor units (paise); the tax is rounded HALF_UP.
 */
public final class TdsCalculator {

    private TdsCalculator() {}

    /** Tax at source = gross · rateBps / 10000, HALF_UP to whole minor units. */
    public static long tax(long grossMinor, int rateBps) {
        if (grossMinor < 0) {
            throw new IllegalArgumentException("grossMinor must be non-negative");
        }
        if (rateBps < 0 || rateBps > 10_000) {
            throw new IllegalArgumentException("rateBps must be between 0 and 10000");
        }
        return BigDecimal.valueOf(grossMinor)
                .multiply(BigDecimal.valueOf(rateBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * The Indian financial-year quarter (Apr–Mar) a date falls in, as {@code "<fyStartYear>-Q<n>"}.
     * Q1 = Apr–Jun, Q2 = Jul–Sep, Q3 = Oct–Dec, Q4 = Jan–Mar; the FY label is its starting year, so a
     * Jan–Mar date belongs to the year <em>before</em> its calendar year (e.g. 2027-01-20 → 2026-Q4).
     */
    public static String quarterOf(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        int month = date.getMonthValue();
        int fyStartYear = month >= 4 ? date.getYear() : date.getYear() - 1;
        int quarter;
        if (month >= 4 && month <= 6) {
            quarter = 1;
        } else if (month >= 7 && month <= 9) {
            quarter = 2;
        } else if (month >= 10 && month <= 12) {
            quarter = 3;
        } else { // Jan–Mar
            quarter = 4;
        }
        return fyStartYear + "-Q" + quarter;
    }
}
