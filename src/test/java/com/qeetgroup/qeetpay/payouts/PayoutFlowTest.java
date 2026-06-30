package com.qeetgroup.qeetpay.payouts;

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
 * Payout flow (TAD Module 02): create stages PENDING_APPROVAL (maker-checker — no disbursement until
 * approved); approve posts the ledger entry (debit liability / credit bank) and is idempotent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PayoutFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PayoutService payouts;
    @Autowired LedgerService ledger;

    @Test
    void createStagesThenApprovePostsToLedger() {
        UUID merchantId = newMerchant();

        Payout created =
                payouts.create(merchantId, 100000, "INR", PayoutRail.IMPS, "acme@bank", "vendor pay");
        assertThat(created.getStatus()).isEqualTo(PayoutStatus.PENDING_APPROVAL); // maker step only
        assertThat(created.getLedgerEntryId()).isNull();

        Payout paid = payouts.approve(merchantId, created.getId());
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getLedgerEntryId()).isNotNull();

        // debit liability / credit bank: a credit to the debit-normal bank account is money out.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-100000);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "liability"))).isEqualTo(-100000);
    }

    @Test
    void approveIsIdempotentAndNeverDoublePosts() {
        UUID merchantId = newMerchant();
        Payout p = payouts.create(merchantId, 50000, "INR", PayoutRail.UPI, "x@bank", null);

        payouts.approve(merchantId, p.getId());
        payouts.approve(merchantId, p.getId()); // no-op replay

        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-50000);
    }

    @Test
    void rejectedPayoutCannotBeApproved() {
        UUID merchantId = newMerchant();
        Payout p = payouts.create(merchantId, 50000, "INR", PayoutRail.NEFT, "y@bank", null);

        Payout rejected = payouts.reject(merchantId, p.getId());
        assertThat(rejected.getStatus()).isEqualTo(PayoutStatus.REJECTED);

        assertThatThrownBy(() -> payouts.approve(merchantId, p.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("po-" + UUID.randomUUID().toString().substring(0, 8), "Payout Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
