package com.qeetgroup.qeetpay.esg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure carbon arithmetic: per-method base + per-rupee marginal footprint, and offset pricing. */
class CarbonCalculatorTest {

    @Test
    void footprintIsBasePlusPerRupeeMarginal() {
        // ₹1000 = 100,000 paise → marginal 100000·2/10000 = 20 g, plus the per-method base.
        assertThat(CarbonCalculator.gramsCo2(CarbonMethod.UPI, 100_000)).isEqualTo(22); // 2 + 20
        assertThat(CarbonCalculator.gramsCo2(CarbonMethod.CARD, 100_000)).isEqualTo(28); // 8 + 20
        assertThat(CarbonCalculator.gramsCo2(CarbonMethod.NET_BANKING, 50_000)).isEqualTo(15); // 5 + 10
        assertThat(CarbonCalculator.gramsCo2(CarbonMethod.WALLET, 0)).isEqualTo(3); // 3 + 0
    }

    @Test
    void footprintRoundsMarginalHalfUp() {
        // ₹125 = 12,500 paise → 12500·2/10000 = 2.5 g → HALF_UP → 3, plus UPI base 2 = 5.
        assertThat(CarbonCalculator.gramsCo2(CarbonMethod.UPI, 12_500)).isEqualTo(5);
    }

    @Test
    void offsetCostPricesPerTonne() {
        // 1 tonne (1,000,000 g) at ₹500/tonne (50,000 paise) → ₹500 = 50,000 paise.
        assertThat(CarbonCalculator.offsetCostMinor(1_000_000, 50_000)).isEqualTo(50_000);
        assertThat(CarbonCalculator.offsetCostMinor(500_000, 50_000)).isEqualTo(25_000);
    }

    @Test
    void offsetCostRoundsHalfUp() {
        // 333,333 g at ₹300/tonne (30,000 paise) → 9,999,990,000/1,000,000 = 9999.99 → HALF_UP → 10000.
        assertThat(CarbonCalculator.offsetCostMinor(333_333, 30_000)).isEqualTo(10_000);
    }

    @Test
    void offsetCostIsZeroWhenUnpriced() {
        assertThat(CarbonCalculator.offsetCostMinor(1_000_000, 0)).isZero();
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> CarbonCalculator.gramsCo2(CarbonMethod.UPI, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullMethod() {
        assertThatThrownBy(() -> CarbonCalculator.gramsCo2(null, 100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeOffsetInputs() {
        assertThatThrownBy(() -> CarbonCalculator.offsetCostMinor(-1, 50_000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CarbonCalculator.offsetCostMinor(1_000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
