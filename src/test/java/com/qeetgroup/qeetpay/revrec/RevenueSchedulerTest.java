package com.qeetgroup.qeetpay.revrec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure allocation logic (no Spring): even split with the remainder landing on the final period. */
class RevenueSchedulerTest {

    @Test
    void straightLineSplitsEvenlyWhenDivisible() {
        List<RevenueScheduler.Slice> slices =
                RevenueScheduler.straightLine(120_000, LocalDate.of(2026, 4, 1), 12);

        assertThat(slices).hasSize(12);
        assertThat(slices).allSatisfy(s -> assertThat(s.amountMinor()).isEqualTo(10_000));
        assertThat(slices.stream().mapToLong(RevenueScheduler.Slice::amountMinor).sum()).isEqualTo(120_000);
        assertThat(slices.get(0).periodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(slices.get(0).periodEnd()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(slices.get(11).periodEnd()).isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void straightLinePutsRemainderOnLastPeriod() {
        List<RevenueScheduler.Slice> slices =
                RevenueScheduler.straightLine(100_000, LocalDate.of(2026, 4, 1), 3);

        assertThat(slices).extracting(RevenueScheduler.Slice::amountMinor)
                .containsExactly(33_333L, 33_333L, 33_334L);
        assertThat(slices.stream().mapToLong(RevenueScheduler.Slice::amountMinor).sum()).isEqualTo(100_000);
    }

    @Test
    void immediateIsASingleSlice() {
        List<RevenueScheduler.Slice> slices =
                RevenueScheduler.immediate(50_000, LocalDate.of(2026, 4, 1));

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).amountMinor()).isEqualTo(50_000);
        assertThat(slices.get(0).periodStart()).isEqualTo(slices.get(0).periodEnd());
    }

    @Test
    void rejectsAmountTooSmallForPeriods() {
        assertThatThrownBy(() -> RevenueScheduler.straightLine(5, LocalDate.of(2026, 4, 1), 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveInputs() {
        assertThatThrownBy(() -> RevenueScheduler.straightLine(0, LocalDate.of(2026, 4, 1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RevenueScheduler.straightLine(1000, LocalDate.of(2026, 4, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
