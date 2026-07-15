package com.qeetgroup.qeetpay.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure split arithmetic: commission + GST + statutory TCS/TDS, with the seller net as the remainder. */
class SplitCalculatorTest {

    @Test
    void computesCommissionGstTcsTdsAndNet() {
        // ₹1000 gross, 5% commission, 18% GST on commission, 1% TCS, 1% TDS.
        SplitCalculator.Breakdown b = SplitCalculator.compute(100_000, 500, 18, 100, 100);

        assertThat(b.commissionMinor()).isEqualTo(5_000);
        assertThat(b.commissionGstMinor()).isEqualTo(900);
        assertThat(b.tcsMinor()).isEqualTo(1_000);
        assertThat(b.tdsMinor()).isEqualTo(1_000);
        assertThat(b.netMinor()).isEqualTo(92_100);
    }

    @Test
    void everyPaiseIsAccountedFor() {
        SplitCalculator.Breakdown b = SplitCalculator.compute(99_999, 275, 18, 100, 100);
        long reconstructed =
                b.commissionMinor() + b.commissionGstMinor() + b.tcsMinor() + b.tdsMinor() + b.netMinor();
        assertThat(reconstructed).isEqualTo(b.grossMinor());
    }

    @Test
    void appliesStatutoryDefaults() {
        assertThat(SplitCalculator.DEFAULT_TCS_BPS).isEqualTo(100);
        assertThat(SplitCalculator.DEFAULT_TDS_BPS).isEqualTo(100);
        assertThat(SplitCalculator.DEFAULT_COMMISSION_GST_RATE).isEqualTo(18);
    }

    @Test
    void rejectsDeductionsExceedingGross() {
        assertThatThrownBy(() -> SplitCalculator.compute(1_000, 10_000, 18, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveGross() {
        assertThatThrownBy(() -> SplitCalculator.compute(0, 500, 18, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
