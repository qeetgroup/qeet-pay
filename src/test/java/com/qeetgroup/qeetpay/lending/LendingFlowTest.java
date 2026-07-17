package com.qeetgroup.qeetpay.lending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Embedded-lending flow (PRD Module 10): an offer is underwritten and accepted (disbursement debits
 * settlement + fees / credits loan_payable), and settlement sweeps repay it (debit loan_payable /
 * credit settlement) until loan_payable nets to zero and the loan is REPAID. Amounts are paise.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LendingFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired LendingService lending;
    @Autowired LedgerService ledger;

    @Test
    void underwriteDisburseAndFullyRepay() {
        UUID merchantId = newMerchant();

        LoanOffer offer = lending.requestOffer(merchantId, "INR", 50_000_000L); // ₹5,00,000/month
        assertThat(offer.getStatus()).isEqualTo(LoanOfferStatus.OFFERED);
        assertThat(offer.getPrincipalMinor()).isEqualTo(50_000_000L);
        assertThat(offer.getFeeMinor()).isEqualTo(3_000_000L);          // 6% of principal
        assertThat(offer.getTotalRepayableMinor()).isEqualTo(53_000_000L);

        LendingService.LoanWithRepayments accepted = lending.acceptOffer(merchantId, offer.getId());
        assertThat(accepted.loan().getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(accepted.loan().getOutstandingMinor()).isEqualTo(53_000_000L);

        // Disbursement postings.
        assertThat(balance(merchantId, "settlement")).isEqualTo(50_000_000L);
        assertThat(balance(merchantId, "fees")).isEqualTo(3_000_000L);
        assertThat(balance(merchantId, "loan_payable")).isEqualTo(53_000_000L);

        UUID loanId = accepted.loan().getId();

        // A ₹1,00,000 settlement sweeps 15% = ₹15,000.
        Loan afterFirst = lending.applyRepayment(merchantId, loanId, 10_000_000L, "settle-1");
        assertThat(afterFirst.getOutstandingMinor()).isEqualTo(51_500_000L); // 53,000,000 − 1,500,000
        assertThat(balance(merchantId, "loan_payable")).isEqualTo(51_500_000L);

        // A large settlement clears the remainder (sweep is capped at the outstanding balance).
        Loan afterSecond = lending.applyRepayment(merchantId, loanId, 500_000_000L, "settle-2");
        assertThat(afterSecond.getStatus()).isEqualTo(LoanStatus.REPAID);
        assertThat(afterSecond.getOutstandingMinor()).isZero();
        assertThat(balance(merchantId, "loan_payable")).isZero();
        // Merchant received principal, repaid principal + fee: settlement net is −fee.
        assertThat(balance(merchantId, "settlement")).isEqualTo(-3_000_000L);
        assertThat(lending.getLoan(merchantId, loanId).repayments()).hasSize(2);
    }

    @Test
    void notEligibleBelowMinimumThroughput() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() -> lending.requestOffer(merchantId, "INR", 1_000_000L)) // ₹10,000/month
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tinySettlementSweepsNothing() {
        UUID merchantId = newMerchant();
        LoanOffer offer = lending.requestOffer(merchantId, "INR", 50_000_000L);
        UUID loanId = lending.acceptOffer(merchantId, offer.getId()).loan().getId();

        Loan unchanged = lending.applyRepayment(merchantId, loanId, 3L, "dust"); // 15% of 3p rounds to 0
        assertThat(unchanged.getOutstandingMinor()).isEqualTo(53_000_000L);
        assertThat(lending.getLoan(merchantId, loanId).repayments()).isEmpty();
    }

    private UUID newMerchant() {
        return merchants.create("lnd-" + UUID.randomUUID().toString().substring(0, 8), "Lending Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
