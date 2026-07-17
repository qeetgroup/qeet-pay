package com.qeetgroup.qeetpay.bnpl;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
import java.util.List;
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
 * BNPL flow (PRD Module 10): creating an agreement funds the merchant the full order amount upfront
 * (debit settlement / credit revenue) and schedules the customer's installments; paying every
 * installment settles the agreement without touching the merchant ledger. Interest is added to the
 * order amount and the rounding remainder is carried on the last installment.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BnplFlowTest {

    private static final LocalDate FIRST_DUE = LocalDate.of(2026, 5, 1);

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BnplService bnpl;
    @Autowired LedgerService ledger;

    @Test
    void createFundsMerchantUpfrontAndPayingAllInstallmentsSettles() {
        UUID merchantId = newMerchant();

        BnplService.AgreementWithInstallments created =
                bnpl.createAgreement(merchantId, "cust-1", "order-1", 1_200_000L, "INR", 12, 0, FIRST_DUE);
        UUID agreementId = created.agreement().getId();

        assertThat(created.agreement().getStatus()).isEqualTo(BnplStatus.ACTIVE);
        // Merchant funded the full order amount immediately: settlement debited, revenue credited.
        assertThat(balance(merchantId, "settlement")).isEqualTo(1_200_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(1_200_000L);
        // 12 equal installments of ₹1000 each.
        assertThat(created.installments()).hasSize(12);
        assertThat(created.installments()).allSatisfy(i -> assertThat(i.getAmountMinor()).isEqualTo(100_000L));

        // Customer repays every installment (state only — the ledger must not move).
        for (int seq = 0; seq < 12; seq++) {
            bnpl.payInstallment(merchantId, agreementId, seq);
        }

        BnplAgreement settled = bnpl.getAgreement(merchantId, agreementId).agreement();
        assertThat(settled.getStatus()).isEqualTo(BnplStatus.SETTLED);
        assertThat(settled.getPaidInstallments()).isEqualTo(12);
        assertThat(settled.getSettledAt()).isNotNull();
        // Repayments never posted to the merchant ledger.
        assertThat(balance(merchantId, "settlement")).isEqualTo(1_200_000L);

        // Paying an already-paid installment is a no-op.
        BnplService.AgreementWithInstallments again = bnpl.payInstallment(merchantId, agreementId, 0);
        assertThat(again.agreement().getPaidInstallments()).isEqualTo(12);
    }

    @Test
    void interestIsAddedAndRemainderCarriedOnLastInstallment() {
        UUID merchantId = newMerchant();

        // ₹10,000 order + 10% interest = ₹11,000 total, split 3 ways.
        BnplService.AgreementWithInstallments created =
                bnpl.createAgreement(merchantId, "cust-2", "order-2", 1_000_000L, "INR", 3, 1000, FIRST_DUE);

        assertThat(created.agreement().getTotalPayableMinor()).isEqualTo(1_100_000L);
        List<BnplInstallment> installments = created.installments();
        assertThat(installments).hasSize(3);
        assertThat(installments.get(0).getAmountMinor()).isEqualTo(366_666L);
        assertThat(installments.get(1).getAmountMinor()).isEqualTo(366_666L);
        assertThat(installments.get(2).getAmountMinor()).isEqualTo(366_668L); // remainder on the last
        // Funding is on the order amount, not the interest-bearing total.
        assertThat(balance(merchantId, "settlement")).isEqualTo(1_000_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(1_000_000L);
    }

    private UUID newMerchant() {
        return merchants.create("bnpl-" + UUID.randomUUID().toString().substring(0, 8), "BNPL Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
