package com.qeetgroup.qeetpay.lending;

/**
 * Underwriting output (PRD Module 10): the principal offered, the factor fee (basis points of
 * principal), the share of each future settlement swept toward repayment, and how long the offer is
 * valid. A {@code principalMinor} of 0 means "not eligible".
 */
public record OfferTerms(long principalMinor, int feeBps, int repaymentPercentBps, int validityDays) {

    public static final OfferTerms NOT_ELIGIBLE = new OfferTerms(0, 0, 1, 0);

    public boolean eligible() {
        return principalMinor > 0;
    }
}
