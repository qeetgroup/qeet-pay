package com.qeetgroup.qeetpay.bnpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * BNPL installment-schedule arithmetic (PRD Module 10 "Embedded Finance"). Pure + deterministic — no
 * Spring/DB — so it is unit-testable in isolation like {@link
 * com.qeetgroup.qeetpay.marketplace.SplitCalculator}. All money is integer minor units. Flat interest
 * ({@code interestBps}, rounded HALF_UP) is added to the order amount to form the total payable, which
 * is split into {@code installments} equal parts; the rounding remainder is carried on the last
 * installment so the slices sum back to the total exactly. Due dates fall one month apart from
 * {@code firstDueDate}.
 */
public final class InstallmentCalculator {

    private InstallmentCalculator() {}

    /** One slice of the repayment plan: 0-based sequence, its due date, and its amount in minor units. */
    public record Installment(int seq, LocalDate dueDate, long amountMinor) {}

    public static List<Installment> schedule(
            long orderAmountMinor, int installments, int interestBps, LocalDate firstDueDate) {
        if (installments < 1) {
            throw new IllegalArgumentException("installments must be at least 1");
        }
        if (orderAmountMinor <= 0) {
            throw new IllegalArgumentException("order amount must be positive");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("firstDueDate is required");
        }

        long totalPayableMinor = orderAmountMinor + bps(orderAmountMinor, interestBps);
        long base = totalPayableMinor / installments;
        if (base < 1) {
            throw new IllegalArgumentException(
                    "total payable " + totalPayableMinor + " is too small for " + installments + " installments");
        }

        List<Installment> slices = new ArrayList<>(installments);
        long allocated = 0;
        for (int seq = 0; seq < installments; seq++) {
            boolean last = seq == installments - 1;
            long amountMinor = last ? totalPayableMinor - allocated : base;
            allocated += amountMinor;
            slices.add(new Installment(seq, firstDueDate.plusMonths(seq), amountMinor));
        }
        return slices;
    }

    /** amount · bps / 10000, HALF_UP to whole minor units. */
    private static long bps(long amountMinor, int basisPoints) {
        return BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
