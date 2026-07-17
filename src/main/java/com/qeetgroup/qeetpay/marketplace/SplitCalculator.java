package com.qeetgroup.qeetpay.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Marketplace split arithmetic (TAD §5 "Marketplace"). Pure + deterministic — no Spring/DB — so it is
 * unit-testable in isolation like {@link com.qeetgroup.qeetpay.gst.GstCalculator}. All money is integer
 * minor units; each deduction is rounded HALF_UP. From a seller's gross, the operator computes:
 *
 * <ul>
 *   <li><b>commission</b> = gross · commissionBps (operator's fee)</li>
 *   <li><b>commission GST</b> = commission · commissionGstRate% (operator's taxable service to the seller)</li>
 *   <li><b>TCS</b> = gross · tcsBps (CGST Act §52 — collected, owed to govt)</li>
 *   <li><b>TDS</b> = gross · tdsBps (Income-Tax §194-O — deducted, owed to govt)</li>
 *   <li><b>net</b> = gross − commission − commission GST − TCS − TDS (the seller's payable)</li>
 * </ul>
 */
public final class SplitCalculator {

    /** Statutory defaults: 1% TCS (§52) and 1% TDS (§194-O), each 100 basis points. */
    public static final int DEFAULT_TCS_BPS = 100;
    public static final int DEFAULT_TDS_BPS = 100;
    public static final int DEFAULT_COMMISSION_GST_RATE = 18;

    private SplitCalculator() {}

    /** The full per-seller breakdown of a split line, in minor units. */
    public record Breakdown(
            long grossMinor,
            long commissionMinor,
            long commissionGstMinor,
            long tcsMinor,
            long tdsMinor,
            long netMinor) {}

    public static Breakdown compute(
            long grossMinor, int commissionBps, int commissionGstRate, int tcsBps, int tdsBps) {
        if (grossMinor <= 0) {
            throw new IllegalArgumentException("gross must be positive");
        }
        requireRange("commissionBps", commissionBps, 0, 10_000);
        requireRange("commissionGstRate", commissionGstRate, 0, 100);
        requireRange("tcsBps", tcsBps, 0, 10_000);
        requireRange("tdsBps", tdsBps, 0, 10_000);

        BigDecimal gross = BigDecimal.valueOf(grossMinor);
        long commission = bps(gross, commissionBps);
        long commissionGst = pct(BigDecimal.valueOf(commission), commissionGstRate);
        long tcs = bps(gross, tcsBps);
        long tds = bps(gross, tdsBps);
        long net = grossMinor - commission - commissionGst - tcs - tds;
        if (net <= 0) {
            throw new IllegalArgumentException(
                    "deductions (" + (grossMinor - net) + ") exceed seller gross " + grossMinor);
        }
        return new Breakdown(grossMinor, commission, commissionGst, tcs, tds, net);
    }

    private static void requireRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }

    /** amount · bps / 10000, HALF_UP to whole minor units. */
    private static long bps(BigDecimal amountMinor, int basisPoints) {
        return amountMinor
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** amount · pct / 100, HALF_UP to whole minor units. */
    private static long pct(BigDecimal amountMinor, int percent) {
        return amountMinor
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
