package com.qeetgroup.qeetpay.insurance;

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
 * Embedded-insurance flow (PRD Module 10): issuing a policy collects its premium into the
 * insurance_reserve; approving a claim pays it out of the reserve back to settlement; a rejected claim
 * moves no money; and a claim above the cover is refused.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class InsuranceFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired InsuranceService insurance;
    @Autowired LedgerService ledger;

    @Test
    void issueFileApproveMovesReserveAndSettlement() {
        UUID merchantId = newMerchant();

        InsuranceService.PolicyWithClaims issued =
                insurance.issuePolicy(
                        merchantId, InsuranceProduct.PAYMENT_PROTECTION, "holder-1", 5_000L, 500_000L, "INR");
        UUID policyId = issued.policy().getId();
        assertThat(issued.policy().getStatus()).isEqualTo(PolicyStatus.ACTIVE);
        // Premium collected: settlement debited, insurance_reserve credited.
        assertThat(balance(merchantId, "insurance_reserve")).isEqualTo(5_000L);
        assertThat(balance(merchantId, "settlement")).isEqualTo(5_000L);

        // File and approve a ₹30 claim → paid out of the reserve.
        InsuranceClaim filed = insurance.fileClaim(merchantId, policyId, 3_000L, "fraudulent charge");
        assertThat(filed.getStatus()).isEqualTo(ClaimStatus.FILED);
        InsuranceClaim paid = insurance.approveClaim(merchantId, filed.getId());
        assertThat(paid.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(paid.getPayoutEntryId()).isNotNull();
        assertThat(balance(merchantId, "insurance_reserve")).isEqualTo(2_000L);
        assertThat(balance(merchantId, "settlement")).isEqualTo(2_000L);

        // A separate claim can be rejected with no ledger movement.
        InsuranceClaim rejected =
                insurance.rejectClaim(
                        merchantId,
                        insurance.fileClaim(merchantId, policyId, 1_000L, "duplicate").getId(),
                        "not covered");
        assertThat(rejected.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(balance(merchantId, "insurance_reserve")).isEqualTo(2_000L);
        assertThat(balance(merchantId, "settlement")).isEqualTo(2_000L);

        assertThat(insurance.getPolicy(merchantId, policyId).claims()).hasSize(2);
    }

    @Test
    void claimAboveCoverIsRejected() {
        UUID merchantId = newMerchant();
        UUID policyId =
                insurance
                        .issuePolicy(merchantId, InsuranceProduct.FRAUD_COVER, "holder-2", 2_000L, 100_000L, "INR")
                        .policy()
                        .getId();
        assertThatThrownBy(() -> insurance.fileClaim(merchantId, policyId, 200_000L, "too big"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID newMerchant() {
        return merchants.create("ins-" + UUID.randomUUID().toString().substring(0, 8), "Insurance Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
