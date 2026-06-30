package com.qeetgroup.qeetpay.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GST computation engine (TAD §7.2). Pure + deterministic — no Spring/DB — so it is unit-testable in
 * isolation. All money is integer minor units (paise); tax is rounded per line with HALF_UP, the
 * GSTN-mandated convention. Intra-state supply splits the rate into equal CGST + SGST; inter-state
 * applies the full rate as IGST. Rate 0 (exempt / nil / zero-rated) yields no tax.
 */
public final class GstCalculator {

    private GstCalculator() {}

    /** Computed tax components for a taxable amount, in minor units. */
    public record GstAmounts(long cgstMinor, long sgstMinor, long igstMinor) {
        public long totalGstMinor() {
            return cgstMinor + sgstMinor + igstMinor;
        }
    }

    public static GstAmounts compute(long taxableMinor, int gstRate, SupplyType supplyType) {
        if (taxableMinor < 0) {
            throw new IllegalArgumentException("taxable amount must be non-negative");
        }
        if (gstRate < 0) {
            throw new IllegalArgumentException("gst rate must be non-negative");
        }
        if (gstRate == 0) {
            return new GstAmounts(0, 0, 0);
        }
        BigDecimal taxable = BigDecimal.valueOf(taxableMinor);
        if (supplyType == SupplyType.INTRA_STATE) {
            long half = pct(taxable, gstRate / 2.0);
            return new GstAmounts(half, half, 0);
        }
        return new GstAmounts(0, 0, pct(taxable, gstRate));
    }

    /** taxable · pct%, rounded HALF_UP to whole minor units. */
    private static long pct(BigDecimal taxableMinor, double percent) {
        return taxableMinor
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
