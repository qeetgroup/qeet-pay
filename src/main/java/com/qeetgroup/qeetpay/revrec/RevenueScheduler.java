package com.qeetgroup.qeetpay.revrec;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Allocates a contract amount into per-period slices (TAD §5 "RevRec"). Pure + deterministic — no
 * Spring/DB — so it is unit-testable in isolation like {@link com.qeetgroup.qeetpay.gst.GstCalculator}.
 * All money is integer minor units; the last period absorbs the rounding remainder so
 * Σ(slices) == totalMinor exactly (no paise is ever created or lost).
 */
public final class RevenueScheduler {

    private RevenueScheduler() {}

    /** One period's planned recognition: a half-open interval [periodStart, periodEnd) and its amount. */
    public record Slice(int seq, LocalDate periodStart, LocalDate periodEnd, long amountMinor) {}

    /**
     * Spreads {@code totalMinor} evenly over {@code periods} consecutive one-month periods starting
     * at {@code start}. The final period takes the remainder so the sum is exact.
     */
    public static List<Slice> straightLine(long totalMinor, LocalDate start, int periods) {
        if (totalMinor <= 0) {
            throw new IllegalArgumentException("totalMinor must be positive");
        }
        if (periods < 1) {
            throw new IllegalArgumentException("periods must be at least 1");
        }
        if (start == null) {
            throw new IllegalArgumentException("start date is required");
        }
        long base = totalMinor / periods;
        if (base < 1) {
            throw new IllegalArgumentException("totalMinor too small to split across " + periods + " periods");
        }
        long remainder = totalMinor - base * periods; // 0 <= remainder < periods
        List<Slice> slices = new ArrayList<>(periods);
        for (int i = 0; i < periods; i++) {
            LocalDate periodStart = start.plusMonths(i);
            LocalDate periodEnd = start.plusMonths(i + 1L);
            long amount = base + (i == periods - 1 ? remainder : 0);
            slices.add(new Slice(i, periodStart, periodEnd, amount));
        }
        return slices;
    }

    /** A single point-in-time slice: the whole amount, earned at {@code start}. */
    public static List<Slice> immediate(long totalMinor, LocalDate start) {
        if (totalMinor <= 0) {
            throw new IllegalArgumentException("totalMinor must be positive");
        }
        if (start == null) {
            throw new IllegalArgumentException("start date is required");
        }
        return List.of(new Slice(0, start, start, totalMinor));
    }

    /** Allocates by {@link RecognitionMethod}; {@code periods} is ignored for {@code IMMEDIATE}. */
    public static List<Slice> allocate(
            RecognitionMethod method, long totalMinor, LocalDate start, int periods) {
        return switch (method) {
            case STRAIGHT_LINE -> straightLine(totalMinor, start, periods);
            case IMMEDIATE -> immediate(totalMinor, start);
        };
    }
}
