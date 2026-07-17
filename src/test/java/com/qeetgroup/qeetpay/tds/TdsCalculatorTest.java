package com.qeetgroup.qeetpay.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pure TDS/TCS arithmetic (tax + HALF_UP rounding) and the FY-quarter derivation helper. */
class TdsCalculatorTest {

    @Test
    void computesTaxAtSource() {
        // ₹1000 gross at 10% (1000 bps) => ₹100 tax.
        assertThat(TdsCalculator.tax(100_000, 1000)).isEqualTo(10_000);
    }

    @Test
    void roundsHalfUp() {
        // 100_005 · 1000 / 10000 = 10000.5 => 10001 (HALF_UP).
        assertThat(TdsCalculator.tax(100_005, 1000)).isEqualTo(10_001);
    }

    @Test
    void rejectsNegativeGross() {
        assertThatThrownBy(() -> TdsCalculator.tax(-1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRateOutOfRange() {
        assertThatThrownBy(() -> TdsCalculator.tax(100_000, 10_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivesIndianFinancialYearQuarter() {
        assertThat(TdsCalculator.quarterOf(LocalDate.of(2026, 4, 1))).isEqualTo("2026-Q1");
        assertThat(TdsCalculator.quarterOf(LocalDate.of(2026, 7, 15))).isEqualTo("2026-Q2");
        assertThat(TdsCalculator.quarterOf(LocalDate.of(2026, 10, 5))).isEqualTo("2026-Q3");
        // Jan–Mar belongs to the FY that started the previous calendar year.
        assertThat(TdsCalculator.quarterOf(LocalDate.of(2027, 1, 20))).isEqualTo("2026-Q4");
        assertThat(TdsCalculator.quarterOf(LocalDate.of(2027, 3, 31))).isEqualTo("2026-Q4");
    }

    @Test
    void rejectsNullDate() {
        assertThatThrownBy(() -> TdsCalculator.quarterOf(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
