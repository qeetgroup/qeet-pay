package com.qeetgroup.qeetpay.esg;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Carbon-footprint arithmetic (PRD Module 16). Pure + deterministic — no Spring/DB — so it is
 * unit-testable in isolation like {@link com.qeetgroup.qeetpay.marketplace.SplitCalculator}. All money
 * is integer minor units (paise) and all footprints are whole grams of CO₂e; the marginal component is
 * rounded HALF_UP. A transaction's footprint is a small fixed per-method base plus a tiny per-rupee
 * marginal factor:
 *
 * <ul>
 *   <li><b>gramsCo2</b> = base(method) + amountMajor · {@value #PER_RUPEE_GRAMS_NUMERATOR}/100 g,
 *       where amountMajor = amountMinor / 100 (so the marginal term is amountMinor · 2 / 10000)</li>
 *   <li><b>offsetCostMinor</b> = grams · pricePerTonneMinor / 1,000,000 (1 tonne = 1e6 g)</li>
 * </ul>
 */
public final class CarbonCalculator {

    /** Fixed per-transaction base footprint by acceptance method, in grams of CO₂e. */
    public static final long BASE_UPI_GRAMS = 2;
    public static final long BASE_CARD_GRAMS = 8;
    public static final long BASE_NET_BANKING_GRAMS = 5;
    public static final long BASE_WALLET_GRAMS = 3;

    /** Marginal footprint of 0.02 g/₹ (2 g per ₹100), applied as amountMinor · 2 / 10000. */
    public static final long PER_RUPEE_GRAMS_NUMERATOR = 2;
    private static final BigDecimal PER_RUPEE_GRAMS_DIVISOR = BigDecimal.valueOf(10_000);

    /** Grams in one tonne — the unit carbon offsets are priced in. */
    private static final BigDecimal GRAMS_PER_TONNE = BigDecimal.valueOf(1_000_000);

    private CarbonCalculator() {}

    /** Estimated CO₂e footprint (whole grams, non-negative) of a payment of {@code amountMinor}. */
    public static long gramsCo2(CarbonMethod method, long amountMinor) {
        if (method == null) {
            throw new IllegalArgumentException("method is required");
        }
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must be non-negative");
        }
        long marginal =
                BigDecimal.valueOf(amountMinor)
                        .multiply(BigDecimal.valueOf(PER_RUPEE_GRAMS_NUMERATOR))
                        .divide(PER_RUPEE_GRAMS_DIVISOR, 0, RoundingMode.HALF_UP)
                        .longValueExact();
        long grams = baseGrams(method) + marginal;
        return Math.max(0, grams);
    }

    /** Cost (minor units, non-negative) of offsetting {@code grams} at {@code pricePerTonneMinor}. */
    public static long offsetCostMinor(long grams, long pricePerTonneMinor) {
        if (grams < 0) {
            throw new IllegalArgumentException("grams must be non-negative");
        }
        if (pricePerTonneMinor < 0) {
            throw new IllegalArgumentException("pricePerTonneMinor must be non-negative");
        }
        long cost =
                BigDecimal.valueOf(grams)
                        .multiply(BigDecimal.valueOf(pricePerTonneMinor))
                        .divide(GRAMS_PER_TONNE, 0, RoundingMode.HALF_UP)
                        .longValueExact();
        return Math.max(0, cost);
    }

    private static long baseGrams(CarbonMethod method) {
        return switch (method) {
            case UPI -> BASE_UPI_GRAMS;
            case CARD -> BASE_CARD_GRAMS;
            case NET_BANKING -> BASE_NET_BANKING_GRAMS;
            case WALLET -> BASE_WALLET_GRAMS;
        };
    }
}
