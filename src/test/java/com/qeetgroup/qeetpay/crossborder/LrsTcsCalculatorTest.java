package com.qeetgroup.qeetpay.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Pure LRS + TCS arithmetic (PRD Module 14.4): the Indian-FY label, and 2.5% TCS charged only on the
 * slice of the cumulative financial-year remittance total that exceeds the LRS threshold.
 */
class LrsTcsCalculatorTest {

    @Test
    void indianFinancialYearLabel() {
        assertThat(LrsTcsCalculator.financialYearOf(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(LrsTcsCalculator.financialYearOf(LocalDate.of(2026, 12, 31))).isEqualTo("2026-27");
        assertThat(LrsTcsCalculator.financialYearOf(LocalDate.of(2027, 1, 15))).isEqualTo("2026-27");
        assertThat(LrsTcsCalculator.financialYearOf(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
        // Jan–Mar belongs to the FY that started the previous calendar year.
        assertThat(LrsTcsCalculator.financialYearOf(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
    }

    @Test
    void noTcsBelowThreshold() {
        // Entire remittance sits under the threshold → nothing taxable.
        assertThat(LrsTcsCalculator.tcsMinor(0L, 500_000L, 1_000_000L, 250)).isZero();
        // Prior + this still under the threshold.
        assertThat(LrsTcsCalculator.tcsMinor(400_000L, 500_000L, 1_000_000L, 250)).isZero();
    }

    @Test
    void taxesOnlyTheSliceAboveThreshold() {
        // prior 0, remit 3,000,000, threshold 1,000,000 → taxable 2,000,000 → 2.5% = 50,000.
        assertThat(LrsTcsCalculator.tcsMinor(0L, 3_000_000L, 1_000_000L, 250)).isEqualTo(50_000L);
        // Remittance straddles the threshold: before 800k, after 1.2M → taxable only 200k → 5,000.
        assertThat(LrsTcsCalculator.tcsMinor(800_000L, 400_000L, 1_000_000L, 250)).isEqualTo(5_000L);
        // Already above the threshold: the whole remittance is taxable.
        assertThat(LrsTcsCalculator.tcsMinor(2_000_000L, 1_000_000L, 1_000_000L, 250)).isEqualTo(25_000L);
    }

    @Test
    void roundsHalfUp() {
        // taxable 101 · 2.5% = 2.525 → 3 (HALF_UP), threshold 0 so all of it is taxable.
        assertThat(LrsTcsCalculator.tcsMinor(0L, 101L, 0L, 250)).isEqualTo(3L);
    }

    @Test
    void defaultRateAndThreshold() {
        long principal = LrsTcsCalculator.LRS_THRESHOLD_INR_MINOR + 10_000_000L; // ₹1,00,000 above
        long tcs = LrsTcsCalculator.tcsMinor(0L, principal);
        assertThat(tcs).isEqualTo(250_000L); // 2.5% of the ₹1,00,000 slice above threshold
    }

    @Test
    void rejectsBadInput() {
        assertThatThrownBy(() -> LrsTcsCalculator.tcsMinor(-1L, 100L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LrsTcsCalculator.tcsMinor(0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LrsTcsCalculator.financialYearOf(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
