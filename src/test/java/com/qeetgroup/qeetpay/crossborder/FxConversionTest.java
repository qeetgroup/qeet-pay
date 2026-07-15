package com.qeetgroup.qeetpay.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pure FX: sandbox rates and the foreign-minor → INR-paise conversion (both use 1/100 minor units). */
class FxConversionTest {

    private final SandboxFxRateAdapter fx = new SandboxFxRateAdapter();

    @Test
    void convertsUsdCentsToInrPaise() {
        // $1000.00 = 100,000 cents; at ₹83.50/USD → ₹83,500.00 = 8,350,000 paise.
        long inr = CrossBorderService.toInrMinor(100_000L, fx.rate("USD", "INR"));
        assertThat(inr).isEqualTo(8_350_000L);
    }

    @Test
    void roundsHalfUp() {
        // 3 cents × 83.50 = 250.5 → 251 paise (HALF_UP).
        assertThat(CrossBorderService.toInrMinor(3L, new BigDecimal("83.50"))).isEqualTo(251L);
    }

    @Test
    void rejectsUnknownCurrencyAndNonInrTarget() {
        assertThatThrownBy(() -> fx.rate("XYZ", "INR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fx.rate("USD", "EUR")).isInstanceOf(IllegalArgumentException.class);
    }
}
