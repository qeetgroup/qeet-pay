package com.qeetgroup.qeetpay.lending;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure underwriting: one month of throughput as principal (capped at ₹25L), 6% fee, 15% sweep.
 * All amounts are paise (₹1 = 100 paise).
 */
class SandboxUnderwritingAdapterTest {

    private final SandboxUnderwritingAdapter adapter = new SandboxUnderwritingAdapter();
    private final UUID merchant = UUID.randomUUID();

    @Test
    void offersOneMonthOfThroughput() {
        OfferTerms terms = adapter.underwrite(merchant, "INR", 50_000_000L); // ₹5,00,000/month
        assertThat(terms.eligible()).isTrue();
        assertThat(terms.principalMinor()).isEqualTo(50_000_000L);
        assertThat(terms.feeBps()).isEqualTo(SandboxUnderwritingAdapter.FACTOR_FEE_BPS);
        assertThat(terms.repaymentPercentBps()).isEqualTo(SandboxUnderwritingAdapter.REPAYMENT_PERCENT_BPS);
        assertThat(terms.validityDays()).isEqualTo(SandboxUnderwritingAdapter.VALIDITY_DAYS);
    }

    @Test
    void capsPrincipalAtTheMaximum() {
        OfferTerms terms = adapter.underwrite(merchant, "INR", 500_000_000L); // ₹50,00,000/month
        assertThat(terms.principalMinor()).isEqualTo(SandboxUnderwritingAdapter.MAX_PRINCIPAL_MINOR);
        assertThat(terms.principalMinor()).isEqualTo(250_000_000L); // ₹25L cap
    }

    @Test
    void rejectsBelowMinimumThroughput() {
        OfferTerms terms = adapter.underwrite(merchant, "INR", 1_000_000L); // ₹10,000/month
        assertThat(terms.eligible()).isFalse();
    }
}
