package com.qeetgroup.qeetpay.bnpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure installment arithmetic: equal split, remainder on the last slice, monthly due dates. */
class InstallmentCalculatorTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 5, 1);

    @Test
    void splitsEvenlyWhenNoInterestAndNoRemainder() {
        List<InstallmentCalculator.Installment> schedule =
                InstallmentCalculator.schedule(1_200_000, 12, 0, FIRST_DUE);

        assertThat(schedule).hasSize(12);
        assertThat(schedule).allSatisfy(i -> assertThat(i.amountMinor()).isEqualTo(100_000));
        assertThat(schedule.stream().mapToLong(InstallmentCalculator.Installment::amountMinor).sum())
                .isEqualTo(1_200_000);
    }

    @Test
    void addsInterestAndCarriesRemainderOnLastInstallment() {
        // ₹10,000 order + 10% interest = ₹11,000 total, split 3 ways: 366,666 + 366,666 + 366,668.
        List<InstallmentCalculator.Installment> schedule =
                InstallmentCalculator.schedule(1_000_000, 3, 1000, FIRST_DUE);

        long total = schedule.stream().mapToLong(InstallmentCalculator.Installment::amountMinor).sum();
        assertThat(total).isEqualTo(1_100_000);
        assertThat(schedule.get(0).amountMinor()).isEqualTo(366_666);
        assertThat(schedule.get(1).amountMinor()).isEqualTo(366_666);
        assertThat(schedule.get(2).amountMinor()).isEqualTo(366_668); // remainder (+2) on the last slice
    }

    @Test
    void dueDatesAreOneMonthApartFromFirst() {
        List<InstallmentCalculator.Installment> schedule =
                InstallmentCalculator.schedule(1_200_000, 12, 0, FIRST_DUE);

        assertThat(schedule.get(0).seq()).isZero();
        assertThat(schedule.get(0).dueDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(schedule.get(1).dueDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(schedule.get(11).seq()).isEqualTo(11);
        assertThat(schedule.get(11).dueDate()).isEqualTo(LocalDate.of(2027, 4, 1));
    }

    @Test
    void rejectsFewerThanOneInstallment() {
        assertThatThrownBy(() -> InstallmentCalculator.schedule(1_200_000, 0, 0, FIRST_DUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveOrderAmount() {
        assertThatThrownBy(() -> InstallmentCalculator.schedule(0, 12, 0, FIRST_DUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScheduleWhosePerInstallmentBaseRoundsToZero() {
        assertThatThrownBy(() -> InstallmentCalculator.schedule(5, 10, 0, FIRST_DUE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
