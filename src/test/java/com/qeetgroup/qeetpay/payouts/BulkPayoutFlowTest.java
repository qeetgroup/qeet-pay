package com.qeetgroup.qeetpay.payouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * Bulk payout flow (TAD §17): create stages every member PENDING_APPROVAL (maker-checker — nothing
 * disburses until the batch is approved); approving disburses each member and posts the ledger; a
 * single member's rail failure is isolated (PARTIALLY_COMPLETED); approve is idempotent; reject
 * closes the batch. Also covers CSV ingestion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BulkPayoutFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BulkPayoutService bulk;
    @Autowired LedgerService ledger;

    @Test
    void createStagesAllPendingThenApproveDisbursesAndPostsLedger() {
        UUID merchantId = newMerchant();

        PayoutBatch created =
                bulk.createBatch(
                        merchantId,
                        "INR",
                        "vendor run",
                        List.of(
                                new PayoutInstruction(100000, PayoutRail.IMPS, "a@bank", "a"),
                                new PayoutInstruction(50000, PayoutRail.UPI, "b@bank", "b"),
                                new PayoutInstruction(25000, PayoutRail.NEFT, "c@bank", "c")));

        assertThat(created.getStatus()).isEqualTo(BatchStatus.PENDING_APPROVAL);
        assertThat(created.getTotalCount()).isEqualTo(3);
        assertThat(created.getTotalAmountMinor()).isEqualTo(175000);
        // maker step only — nothing disbursed yet
        assertThat(bulk.payoutsOf(merchantId, created.getId()))
                .allMatch(p -> p.getStatus() == PayoutStatus.PENDING_APPROVAL);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isZero();

        PayoutBatch approved = bulk.approveBatch(merchantId, created.getId());

        assertThat(approved.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(approved.getPaidCount()).isEqualTo(3);
        assertThat(approved.getFailedCount()).isZero();
        assertThat(bulk.payoutsOf(merchantId, created.getId()))
                .allMatch(p -> p.getStatus() == PayoutStatus.PAID);
        // debit liability / credit bank per member: a credit to the debit-normal bank is money out.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-175000);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "liability"))).isEqualTo(-175000);
    }

    @Test
    void oneFailedMemberYieldsPartiallyCompletedBatch() {
        UUID merchantId = newMerchant();

        PayoutBatch batch =
                bulk.createBatch(
                        merchantId,
                        "INR",
                        "mixed",
                        List.of(
                                new PayoutInstruction(100000, PayoutRail.IMPS, "good@bank", "ok"),
                                new PayoutInstruction(40000, PayoutRail.UPI, "fail@bank", "will fail")));

        PayoutBatch approved = bulk.approveBatch(merchantId, batch.getId());

        assertThat(approved.getStatus()).isEqualTo(BatchStatus.PARTIALLY_COMPLETED);
        assertThat(approved.getPaidCount()).isEqualTo(1);
        assertThat(approved.getFailedCount()).isEqualTo(1);
        // only the successful ₹1000 payout moved money
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-100000);
    }

    @Test
    void approveIsIdempotentAndNeverDoublePosts() {
        UUID merchantId = newMerchant();
        PayoutBatch batch =
                bulk.createBatch(
                        merchantId, "INR", null, List.of(new PayoutInstruction(60000, PayoutRail.UPI, "x@bank", null)));

        bulk.approveBatch(merchantId, batch.getId());
        PayoutBatch replay = bulk.approveBatch(merchantId, batch.getId()); // no-op

        assertThat(replay.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-60000);
    }

    @Test
    void rejectedBatchClosesMembersAndCannotBeApproved() {
        UUID merchantId = newMerchant();
        PayoutBatch batch =
                bulk.createBatch(
                        merchantId,
                        "INR",
                        null,
                        List.of(new PayoutInstruction(60000, PayoutRail.NEFT, "y@bank", null)));

        PayoutBatch rejected = bulk.rejectBatch(merchantId, batch.getId());
        assertThat(rejected.getStatus()).isEqualTo(BatchStatus.REJECTED);
        assertThat(bulk.payoutsOf(merchantId, batch.getId()))
                .allMatch(p -> p.getStatus() == PayoutStatus.REJECTED);

        assertThatThrownBy(() -> bulk.approveBatch(merchantId, batch.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isZero();
    }

    @Test
    void createsBatchFromCsvWithHeaderRow() {
        UUID merchantId = newMerchant();
        String csv =
                """
                rail,destination,amount_minor,description
                IMPS,vendor1@bank,100000,invoice-1
                UPI,vendor2@bank,50000,invoice-2

                NEFT,vendor3@bank,25000,invoice-3
                """;

        PayoutBatch batch = bulk.createBatchFromCsv(merchantId, "INR", "csv run", csv);

        assertThat(batch.getTotalCount()).isEqualTo(3);
        assertThat(batch.getTotalAmountMinor()).isEqualTo(175000);
        assertThat(bulk.approveBatch(merchantId, batch.getId()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-175000);
    }

    private UUID newMerchant() {
        return merchants
                .create("bulk-" + UUID.randomUUID().toString().substring(0, 8), "Bulk Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
