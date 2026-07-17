package com.qeetgroup.qeetpay.lending;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox underwriting (PRD Module 10, TAD §11.1) — deterministic, offline stand-in for a live AA /
 * credit model. Offers one month of settlement throughput as principal (capped at ₹25L, per the PRD),
 * a 6% factor fee, and a 15%-of-settlement sweep, valid 14 days. Merchants below a minimum monthly
 * volume are not eligible. Active whenever no live adapter bean is present.
 */
@Component
@ConditionalOnMissingBean(name = "liveUnderwritingAdapter")
public class SandboxUnderwritingAdapter implements UnderwritingAdapter {

    /** ₹25,00,000 (₹25 lakh) in paise — the working-capital cap from the PRD. */
    static final long MAX_PRINCIPAL_MINOR = 250_000_000L;
    /** ₹50,000/month minimum throughput to qualify, in paise. */
    static final long MIN_MONTHLY_VOLUME_MINOR = 5_000_000L;
    static final int FACTOR_FEE_BPS = 600;        // 6% factor fee
    static final int REPAYMENT_PERCENT_BPS = 1500; // sweep 15% of each settlement
    static final int VALIDITY_DAYS = 14;

    @Override
    public OfferTerms underwrite(UUID merchantId, String currency, long avgMonthlyVolumeMinor) {
        if (avgMonthlyVolumeMinor < MIN_MONTHLY_VOLUME_MINOR) {
            return OfferTerms.NOT_ELIGIBLE;
        }
        long principal = Math.min(avgMonthlyVolumeMinor, MAX_PRINCIPAL_MINOR);
        return new OfferTerms(principal, FACTOR_FEE_BPS, REPAYMENT_PERCENT_BPS, VALIDITY_DAYS);
    }
}
