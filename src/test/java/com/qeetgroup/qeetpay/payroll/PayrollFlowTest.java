package com.qeetgroup.qeetpay.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.payouts.PayoutRail;
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
 * Payroll disbursement flow (PRD Module 02.5): create computes net pay = gross − statutory (PF/ESI/PT/
 * TDS) and stages every line PENDING_APPROVAL (maker step — nothing disburses); approve is the
 * maker-checker step that disburses each net pay through the payouts engine (debit liability / credit
 * bank per line) and captures the payout + ledger reference back onto the line; a single rail failure
 * is isolated (PARTIALLY_DISBURSED); penny-drop rejection blocks staging; approve is idempotent.
 *
 * <p>Mirrors the payouts flow tests. This repo has no {@code AbstractIntegrationTest}; the module uses
 * the same per-class Testcontainers Postgres the sibling flow tests use.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PayrollFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PayrollService payroll;
    @Autowired LedgerService ledger;

    @Test
    void createComputesNetPayAndStatutoryTotals() {
        UUID merchantId = newMerchant();

        PayrollBatch batch =
                payroll.create(
                        merchantId,
                        "INR",
                        "2026-07",
                        "July payroll",
                        List.of(
                                // gross 100000 − (12000+750+200+5000)=17950 → net 82050
                                line("emp-1", "alice@bank", 100000, 12000, 750, 200, 5000),
                                // gross 60000 − (7200+450+200+0)=7850 → net 52150
                                line("emp-2", "bob@bank", 60000, 7200, 450, 200, 0)));

        assertThat(batch.getStatus()).isEqualTo(PayrollBatchStatus.PENDING_APPROVAL);
        assertThat(batch.getLineCount()).isEqualTo(2);
        assertThat(batch.getTotalGrossMinor()).isEqualTo(160000);
        assertThat(batch.getTotalStatutoryMinor()).isEqualTo(25800);
        assertThat(batch.getTotalNetMinor()).isEqualTo(134200);

        List<PayrollLine> lines = payroll.linesOf(merchantId, batch.getId());
        assertThat(lines).allMatch(l -> l.getStatus() == PayrollLineStatus.PENDING);
        assertThat(net(lines, "emp-1")).isEqualTo(82050);
        assertThat(net(lines, "emp-2")).isEqualTo(52150);
        // maker step only — nothing disbursed
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isZero();
    }

    @Test
    void approveDisbursesEveryLineAndPostsLedger() {
        UUID merchantId = newMerchant();
        PayrollBatch created =
                payroll.create(
                        merchantId,
                        "INR",
                        "2026-07",
                        null,
                        List.of(
                                line("emp-1", "alice@bank", 100000, 12000, 750, 200, 5000),
                                line("emp-2", "bob@bank", 60000, 7200, 450, 200, 0)));

        PayrollBatch approved = payroll.approve(merchantId, created.getId());

        assertThat(approved.getStatus()).isEqualTo(PayrollBatchStatus.DISBURSED);
        assertThat(approved.getPaidCount()).isEqualTo(2);
        assertThat(approved.getFailedCount()).isZero();
        assertThat(approved.getPayoutBatchId()).isNotNull();

        List<PayrollLine> lines = payroll.linesOf(merchantId, created.getId());
        assertThat(lines).allMatch(l -> l.getStatus() == PayrollLineStatus.PAID);
        assertThat(lines).allMatch(l -> l.getPayoutId() != null);
        assertThat(lines).allMatch(l -> l.getLedgerEntryId() != null);

        // debit liability / credit bank per line: a credit to the debit-normal bank is money out.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-134200);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "liability"))).isEqualTo(-134200);

        // combined salary slip + receipt reflects the disbursed line.
        PayrollLine alice = byRef(lines, "emp-1");
        SalarySlip slip = payroll.slip(merchantId, created.getId(), alice.getId());
        assertThat(slip.grossMinor()).isEqualTo(100000);
        assertThat(slip.statutoryMinor()).isEqualTo(17950);
        assertThat(slip.netPayMinor()).isEqualTo(82050);
        assertThat(slip.status()).isEqualTo("PAID");
        assertThat(slip.payoutRef()).isNotNull();
        assertThat(slip.ledgerEntryId()).isNotNull();
    }

    @Test
    void oneFailedLineYieldsPartiallyDisbursedRun() {
        UUID merchantId = newMerchant();
        PayrollBatch created =
                payroll.create(
                        merchantId,
                        "INR",
                        "2026-07",
                        null,
                        List.of(
                                line("emp-1", "good@bank", 100000, 12000, 750, 200, 5000), // net 82050
                                line("emp-2", "fail@bank", 60000, 7200, 450, 200, 0))); // rail fails

        PayrollBatch approved = payroll.approve(merchantId, created.getId());

        assertThat(approved.getStatus()).isEqualTo(PayrollBatchStatus.PARTIALLY_DISBURSED);
        assertThat(approved.getPaidCount()).isEqualTo(1);
        assertThat(approved.getFailedCount()).isEqualTo(1);

        List<PayrollLine> lines = payroll.linesOf(merchantId, created.getId());
        assertThat(byRef(lines, "emp-1").getStatus()).isEqualTo(PayrollLineStatus.PAID);
        PayrollLine failed = byRef(lines, "emp-2");
        assertThat(failed.getStatus()).isEqualTo(PayrollLineStatus.FAILED);
        assertThat(failed.getFailureReason()).isNotBlank();

        // only the successful line moved money.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-82050);
    }

    @Test
    void pennyDropRejectionBlocksStaging() {
        UUID merchantId = newMerchant();

        PayrollLineInput bad =
                new PayrollLineInput(
                        "emp-1", "Alice", PayoutRail.IMPS, "alice@bank",
                        "fail_000111", "HDFC0001234", 100000, 12000, 750, 200, 5000);

        assertThatThrownBy(() -> payroll.create(merchantId, "INR", "2026-07", null, List.of(bad)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pennyDropVerifiedDestinationIsFlagged() {
        UUID merchantId = newMerchant();
        PayrollLineInput ok =
                new PayrollLineInput(
                        "emp-1", "Alice", PayoutRail.IMPS, "alice@bank",
                        "50100200300", "HDFC0001234", 100000, 12000, 750, 200, 5000);

        PayrollBatch batch = payroll.create(merchantId, "INR", "2026-07", null, List.of(ok));
        assertThat(payroll.linesOf(merchantId, batch.getId())).allMatch(PayrollLine::isVerified);
    }

    @Test
    void netPayMustBePositive() {
        UUID merchantId = newMerchant();
        // statutory (60000) exceeds gross (50000) → non-positive net rejected.
        PayrollLineInput negative = line("emp-1", "alice@bank", 50000, 40000, 20000, 0, 0);
        assertThatThrownBy(() -> payroll.create(merchantId, "INR", "2026-07", null, List.of(negative)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedRunClosesAndCannotBeApproved() {
        UUID merchantId = newMerchant();
        PayrollBatch created =
                payroll.create(
                        merchantId, "INR", "2026-07", null,
                        List.of(line("emp-1", "alice@bank", 100000, 12000, 750, 200, 5000)));

        PayrollBatch rejected = payroll.reject(merchantId, created.getId());
        assertThat(rejected.getStatus()).isEqualTo(PayrollBatchStatus.REJECTED);

        assertThatThrownBy(() -> payroll.approve(merchantId, created.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isZero();
    }

    @Test
    void approveIsIdempotentAndNeverDoublePosts() {
        UUID merchantId = newMerchant();
        PayrollBatch created =
                payroll.create(
                        merchantId, "INR", "2026-07", null,
                        List.of(line("emp-1", "alice@bank", 100000, 12000, 750, 200, 5000)));

        payroll.approve(merchantId, created.getId());
        PayrollBatch replay = payroll.approve(merchantId, created.getId()); // no-op

        assertThat(replay.getStatus()).isEqualTo(PayrollBatchStatus.DISBURSED);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "bank"))).isEqualTo(-82050);
    }

    private static PayrollLineInput line(
            String employeeRef, String destination, long gross, long pf, long esi, long pt, long tds) {
        return new PayrollLineInput(
                employeeRef, employeeRef, PayoutRail.IMPS, destination, null, null, gross, pf, esi, pt, tds);
    }

    private static long net(List<PayrollLine> lines, String ref) {
        return byRef(lines, ref).getNetPayMinor();
    }

    private static PayrollLine byRef(List<PayrollLine> lines, String ref) {
        return lines.stream()
                .filter(l -> l.getEmployeeRef().equals(ref))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no line for " + ref));
    }

    private UUID newMerchant() {
        return merchants
                .create("payroll-" + UUID.randomUUID().toString().substring(0, 8), "Payroll Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
