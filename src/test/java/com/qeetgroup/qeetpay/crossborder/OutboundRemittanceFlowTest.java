package com.qeetgroup.qeetpay.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Outbound / import remittance flow (PRD Module 14.4): a foreign-vendor payment is quoted (FX + LRS
 * TCS), created — debiting the merchant's settlement for INR principal + 2.5% TCS above the LRS
 * threshold, crediting bank (the SWIFT principal) and tax_payable (the TCS) in one balanced entry —
 * then the LRS financial-year running total accumulates across remittances, and a failed wire posts the
 * exact offsetting entry.
 */
class OutboundRemittanceFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired OutboundRemittanceService outbound;
    @Autowired LedgerService ledger;

    @Test
    void tcsAboveThresholdWithBalancedLedgerPostings() {
        UUID merchantId = newMerchant();

        // $20,000 at ₹83.50 = ₹16,70,000 principal — comfortably above the LRS threshold.
        long usdCents = 2_000_000L;
        OutboundRemittanceService.Quote quote = outbound.quote(merchantId, "USD", usdCents);
        assertThat(quote.principalInrMinor()).isEqualTo(167_000_000L);

        long expectedTcs = LrsTcsCalculator.tcsMinor(0L, quote.principalInrMinor());
        assertThat(expectedTcs).isPositive(); // premise: this remittance is above the threshold
        assertThat(quote.tcsMinor()).isEqualTo(expectedTcs);
        assertThat(quote.inrDebitedMinor()).isEqualTo(167_000_000L + expectedTcs);

        OutboundRemittance r =
                outbound.create(
                        merchantId, "Acme Cloud Inc", "CHASUS33", "US64...", "US", "USD", usdCents, "S0801");
        assertThat(r.getStatus()).isEqualTo(OutboundRemittanceStatus.CREATED);
        assertThat(r.getTcsMinor()).isEqualTo(expectedTcs);
        assertThat(r.getInrDebitedMinor()).isEqualTo(167_000_000L + expectedTcs);
        assertThat(r.getLrsCumulativeAfterMinor()).isEqualTo(167_000_000L);

        // Balanced money-out entry: debit settlement (principal + TCS) = credit bank (principal) +
        // credit tax_payable (TCS). Ledger balances are natural-sign (settlement/bank debit-normal).
        long settlement = balance(merchantId, "settlement");
        long bank = balance(merchantId, "bank");
        long taxPayable = balance(merchantId, "tax_payable");
        assertThat(settlement).isEqualTo(167_000_000L + expectedTcs);
        assertThat(taxPayable).isEqualTo(expectedTcs);
        assertThat(bank).isEqualTo(-167_000_000L); // credited (money out)
        assertThat(settlement).isEqualTo(taxPayable + (-bank)); // Σdebits = Σcredits
    }

    @Test
    void lrsRunningTotalAccumulatesAcrossFinancialYear() {
        UUID merchantId = newMerchant();

        // First: $5,000 = ₹4,17,500 — below the threshold, so no TCS.
        OutboundRemittance first =
                outbound.create(
                        merchantId, "Vendor One", "DEUTDEFF", "DE89...", "DE", "USD", 500_000L, "S0801");
        assertThat(first.getPrincipalInrMinor()).isEqualTo(41_750_000L);
        assertThat(first.getTcsMinor()).isZero();
        assertThat(first.getLrsCumulativeBeforeMinor()).isZero();
        assertThat(first.getLrsCumulativeAfterMinor()).isEqualTo(41_750_000L);

        // A fresh quote now reflects the prior remittance in the LRS running total.
        OutboundRemittanceService.Quote quote2 = outbound.quote(merchantId, "USD", 1_000_000L);
        assertThat(quote2.lrsCumulativeBeforeMinor()).isEqualTo(41_750_000L);

        // Second: $10,000 = ₹8,35,000 — pushes the FY total past the threshold; only the slice is taxed.
        OutboundRemittance second =
                outbound.create(
                        merchantId, "Vendor Two", "DEUTDEFF", "DE89...", "DE", "USD", 1_000_000L, "S0801");
        assertThat(second.getLrsCumulativeBeforeMinor()).isEqualTo(41_750_000L);
        assertThat(second.getLrsCumulativeAfterMinor()).isEqualTo(125_250_000L);
        assertThat(second.getTcsMinor())
                .isEqualTo(LrsTcsCalculator.tcsMinor(41_750_000L, 83_500_000L))
                .isPositive();
        assertThat(second.getFinancialYear()).isEqualTo(first.getFinancialYear());
    }

    @Test
    void markRemittedRecordsReferenceAndEvent() {
        UUID merchantId = newMerchant();
        OutboundRemittance r =
                outbound.create(
                        merchantId, "SaaS Ltd", "BARCGB22", "GB29...", "GB", "GBP", 100_000L, "S0801");

        OutboundRemittance remitted = outbound.markRemitted(merchantId, r.getId(), "MT103-XYZ");
        assertThat(remitted.getStatus()).isEqualTo(OutboundRemittanceStatus.REMITTED);
        assertThat(remitted.getRemittanceReference()).isEqualTo("MT103-XYZ");

        OutboundRemittanceService.RemittanceWithEvents got = outbound.get(merchantId, r.getId());
        assertThat(got.events())
                .extracting(e -> e.getType())
                .containsExactly(OutboundRemittanceEventType.CREATED, OutboundRemittanceEventType.REMITTED);

        assertThatThrownBy(() -> outbound.markRemitted(merchantId, r.getId(), "again"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markFailedPostsOffsettingEntryAndReleasesLrs() {
        UUID merchantId = newMerchant();
        long usdCents = 2_000_000L; // above threshold → non-zero TCS to reverse too
        OutboundRemittance r =
                outbound.create(
                        merchantId, "Bad Vendor", "CHASUS33", "US64...", "US", "USD", usdCents, "S0801");
        assertThat(r.getTcsMinor()).isPositive();

        OutboundRemittance failed = outbound.markFailed(merchantId, r.getId(), "beneficiary rejected");
        assertThat(failed.getStatus()).isEqualTo(OutboundRemittanceStatus.FAILED);
        assertThat(failed.getReversalEntryId()).isNotNull();

        // The offsetting entry nets every account back to zero.
        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "bank")).isZero();
        assertThat(balance(merchantId, "tax_payable")).isZero();

        // A failed/reversed remittance no longer counts toward the LRS running total.
        assertThat(outbound.quote(merchantId, "USD", 100_000L).lrsCumulativeBeforeMinor()).isZero();
    }

    @Test
    void rejectsInrAndMissingBeneficiary() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() -> outbound.create(merchantId, "V", "SW", "AC", "US", "INR", 1000L, "S0801"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> outbound.create(merchantId, " ", "SW", "AC", "US", "USD", 1000L, "S0801"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID newMerchant() {
        return merchants.create("ob-" + UUID.randomUUID().toString().substring(0, 8), "Importer Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
