package com.qeetgroup.qeetpay.crossborder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * LRS (Liberalised Remittance Scheme) + TCS arithmetic for outbound / import remittances (PRD Module
 * 14.4). Pure + deterministic — no Spring/DB — so it is unit-testable in isolation like
 * {@link com.qeetgroup.qeetpay.marketplace.SplitCalculator} and
 * {@link com.qeetgroup.qeetpay.tds.TdsCalculator}. All money is integer minor units (paise); the tax
 * is rounded HALF_UP.
 *
 * <p>Under §206C(1G) of the Income-Tax Act, TCS is collected on outbound remittances only on the
 * portion of the cumulative financial-year remittance that exceeds the LRS threshold. This module
 * models the simplified {@code 2.5%}-above-threshold rate from PRD Module 14.4: for each remittance,
 * the taxable base is the slice of <em>this</em> remittance that pushes the running FY total past the
 * threshold, and TCS = base · {@link #TCS_BPS}.
 */
public final class LrsTcsCalculator {

    /** Simplified LRS TCS rate from PRD Module 14.4: 2.5% = 250 basis points. */
    public static final int TCS_BPS = 250;

    /**
     * Per-financial-year LRS threshold below which no TCS is collected, in INR minor units (paise).
     * ₹10,00,000 = 1,00,000,000 paise (the LRS TCS-free ceiling per FY). Only the cumulative amount
     * above this attracts {@link #TCS_BPS}.
     */
    public static final long LRS_THRESHOLD_INR_MINOR = 100_000_000L;

    private LrsTcsCalculator() {}

    /**
     * The Indian financial year (Apr–Mar) a date falls in, as {@code "<fyStartYear>-<fyEndYY>"}
     * (e.g. {@code 2026-06-10 → "2026-27"}, {@code 2027-02-01 → "2026-27"}). The FY label is its
     * starting year, so a Jan–Mar date belongs to the FY that started the previous calendar year.
     */
    public static String financialYearOf(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endYy = (startYear + 1) % 100;
        return startYear + "-" + String.format("%02d", endYy);
    }

    /**
     * TCS on one remittance, using the default {@link #LRS_THRESHOLD_INR_MINOR} and {@link #TCS_BPS}.
     *
     * @param priorCumulativeInrMinor the merchant's remittance total (INR paise) already recorded this FY
     * @param remittanceInrMinor      this remittance's INR-principal amount (paise)
     */
    public static long tcsMinor(long priorCumulativeInrMinor, long remittanceInrMinor) {
        return tcsMinor(priorCumulativeInrMinor, remittanceInrMinor, LRS_THRESHOLD_INR_MINOR, TCS_BPS);
    }

    /**
     * TCS on one remittance, computed only on the slice of {@code remittanceInrMinor} that carries the
     * running FY total above {@code thresholdMinor}: {@code base = max(0, after − t) − max(0, before − t)}
     * where {@code before}/{@code after} are the cumulative INR before and after this remittance. TCS =
     * {@code base · tcsBps / 10000}, HALF_UP.
     */
    public static long tcsMinor(
            long priorCumulativeInrMinor, long remittanceInrMinor, long thresholdMinor, int tcsBps) {
        if (priorCumulativeInrMinor < 0) {
            throw new IllegalArgumentException("prior cumulative must be non-negative");
        }
        if (remittanceInrMinor <= 0) {
            throw new IllegalArgumentException("remittance amount must be positive");
        }
        if (thresholdMinor < 0) {
            throw new IllegalArgumentException("threshold must be non-negative");
        }
        if (tcsBps < 0 || tcsBps > 10_000) {
            throw new IllegalArgumentException("tcsBps must be between 0 and 10000");
        }
        long before = priorCumulativeInrMinor;
        long after = priorCumulativeInrMinor + remittanceInrMinor;
        long taxableBase = Math.max(0L, after - thresholdMinor) - Math.max(0L, before - thresholdMinor);
        if (taxableBase <= 0L) {
            return 0L;
        }
        return BigDecimal.valueOf(taxableBase)
                .multiply(BigDecimal.valueOf(tcsBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
