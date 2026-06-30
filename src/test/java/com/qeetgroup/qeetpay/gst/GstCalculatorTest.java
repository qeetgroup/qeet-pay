package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.gst.GstCalculator.GstAmounts;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the GST math (no Spring/DB) — including HALF_UP per-line rounding. */
class GstCalculatorTest {

    @Test
    void intraStateSplitsRateIntoCgstAndSgst() {
        GstAmounts a = GstCalculator.compute(100_000, 18, SupplyType.INTRA_STATE); // ₹1,000 @ 18%
        assertThat(a.cgstMinor()).isEqualTo(9_000); // 9%
        assertThat(a.sgstMinor()).isEqualTo(9_000); // 9%
        assertThat(a.igstMinor()).isZero();
        assertThat(a.totalGstMinor()).isEqualTo(18_000);
    }

    @Test
    void interStateUsesFullIgst() {
        GstAmounts a = GstCalculator.compute(100_000, 18, SupplyType.INTER_STATE);
        assertThat(a.igstMinor()).isEqualTo(18_000);
        assertThat(a.cgstMinor()).isZero();
        assertThat(a.sgstMinor()).isZero();
    }

    @Test
    void zeroRateIsExempt() {
        GstAmounts a = GstCalculator.compute(100_000, 0, SupplyType.INTRA_STATE);
        assertThat(a.totalGstMinor()).isZero();
    }

    @Test
    void roundsHalfUpPerLine() {
        // ₹333.33 (33333 paise) @ 18% intra → 9% each = 2999.97 → 3000 paise (HALF_UP).
        GstAmounts a = GstCalculator.compute(33_333, 18, SupplyType.INTRA_STATE);
        assertThat(a.cgstMinor()).isEqualTo(3_000);
        assertThat(a.sgstMinor()).isEqualTo(3_000);
    }
}
