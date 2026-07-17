package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void oddRateSplitsIntoFractionalHalfPercent() {
        // 5% intra-state → 2.5% CGST + 2.5% SGST. ₹1,000 (100000 paise): 2.5% = 2500 paise each.
        GstAmounts a = GstCalculator.compute(100_000, 5, SupplyType.INTRA_STATE);
        assertThat(a.cgstMinor()).isEqualTo(2_500);
        assertThat(a.sgstMinor()).isEqualTo(2_500);
        assertThat(a.totalGstMinor()).isEqualTo(5_000);
    }

    @Test
    void halfUpBoundaryRoundsUpAtExactlyHalfMinorUnit() {
        // 100 paise @ 5% intra → 2.5% each = exactly 2.5 paise → HALF_UP → 3 paise per component.
        GstAmounts a = GstCalculator.compute(100, 5, SupplyType.INTRA_STATE);
        assertThat(a.cgstMinor()).isEqualTo(3);
        assertThat(a.sgstMinor()).isEqualTo(3);
    }

    @Test
    void intraStatePerComponentRoundingCanExceedInterStateByOnePaisa() {
        // Documents the by-design rounding artifact: intra-state rounds each half independently,
        // so 2 * round(2.5) = 6, while inter-state rounds the whole = round(5) = 5.
        long taxable = 100;
        GstAmounts intra = GstCalculator.compute(taxable, 5, SupplyType.INTRA_STATE);
        GstAmounts inter = GstCalculator.compute(taxable, 5, SupplyType.INTER_STATE);
        assertThat(intra.totalGstMinor()).isEqualTo(6);
        assertThat(inter.totalGstMinor()).isEqualTo(5);
    }

    @Test
    void zeroTaxableYieldsNoTax() {
        GstAmounts a = GstCalculator.compute(0, 18, SupplyType.INTRA_STATE);
        assertThat(a.totalGstMinor()).isZero();
    }

    @Test
    void interStateZeroRateIsExempt() {
        GstAmounts a = GstCalculator.compute(100_000, 0, SupplyType.INTER_STATE);
        assertThat(a.igstMinor()).isZero();
        assertThat(a.totalGstMinor()).isZero();
    }

    @Test
    void negativeTaxableIsRejected() {
        assertThatThrownBy(() -> GstCalculator.compute(-1, 18, SupplyType.INTRA_STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void negativeRateIsRejected() {
        assertThatThrownBy(() -> GstCalculator.compute(100, -5, SupplyType.INTRA_STATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void twentyEightPercentSlab() {
        // Top GST slab, intra-state: 14% CGST + 14% SGST on ₹1,000.
        GstAmounts a = GstCalculator.compute(100_000, 28, SupplyType.INTRA_STATE);
        assertThat(a.cgstMinor()).isEqualTo(14_000);
        assertThat(a.sgstMinor()).isEqualTo(14_000);
        assertThat(a.totalGstMinor()).isEqualTo(28_000);
    }
}
