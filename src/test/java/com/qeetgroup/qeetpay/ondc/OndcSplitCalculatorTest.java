package com.qeetgroup.qeetpay.ondc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure ONDC settlement arithmetic: commission + GST on commission + statutory TCS (§52), net remainder. */
class OndcSplitCalculatorTest {

    @Test
    void computesCommissionGstTcsAndNet() {
        // ₹1000 gross, 5% commission, 18% GST on commission, 1% TCS (§52).
        OndcSplitCalculator.Breakdown b = OndcSplitCalculator.compute(100_000, 500, 18, 100);

        assertThat(b.commissionMinor()).isEqualTo(5_000);
        assertThat(b.commissionGstMinor()).isEqualTo(900);
        assertThat(b.tcsMinor()).isEqualTo(1_000);
        assertThat(b.netMinor()).isEqualTo(93_100);
    }

    @Test
    void everyPaiseIsAccountedFor() {
        OndcSplitCalculator.Breakdown b = OndcSplitCalculator.compute(99_999, 275, 18, 100);
        long reconstructed =
                b.commissionMinor() + b.commissionGstMinor() + b.tcsMinor() + b.netMinor();
        assertThat(reconstructed).isEqualTo(b.grossMinor());
    }

    @Test
    void appliesStatutoryDefaults() {
        assertThat(OndcSplitCalculator.DEFAULT_TCS_BPS).isEqualTo(100);
        assertThat(OndcSplitCalculator.DEFAULT_COMMISSION_GST_RATE).isEqualTo(18);
    }

    @Test
    void rejectsDeductionsExceedingGross() {
        assertThatThrownBy(() -> OndcSplitCalculator.compute(1_000, 10_000, 18, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveGross() {
        assertThatThrownBy(() -> OndcSplitCalculator.compute(0, 500, 18, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
