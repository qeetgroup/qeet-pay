package com.qeetgroup.qeetpay.lending;

import java.util.UUID;

/**
 * Pluggable underwriting backend (PRD Module 10, TAD §5) — sandbox or a live Account-Aggregator /
 * credit-model service. Given a merchant and its recent settlement throughput, returns the advance
 * terms to offer (or {@link OfferTerms#NOT_ELIGIBLE}).
 */
public interface UnderwritingAdapter {

    OfferTerms underwrite(UUID merchantId, String currency, long avgMonthlyVolumeMinor);
}
