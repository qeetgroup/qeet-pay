package com.qeetgroup.qeetpay.treasury;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Treasury auto-sweep flow (PRD Novel N3): a rule whose source balance is above its threshold sweeps
 * the surplus (retaining {@code keepMinor}) into the target as a balanced ledger entry and records a
 * {@link SweepExecution}; the {@code keepMinor} buffer is honoured; a PAUSED rule never fires.
 */
class TreasuryFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired TreasuryService treasury;
    @Autowired LedgerService ledger;

    @Test
    void aboveThresholdFiresAndPostsBalancedEntryAndExecution() {
        UUID merchantId = newMerchant();
        seedSettlement(merchantId, 100_000L); // ₹1,000.00 into settlement

        SweepRule rule =
                treasury.createRule(
                        merchantId, "sweep-to-bank", "settlement", "bank",
                        SweepTrigger.THRESHOLD, 50_000L, null, 0L);

        TreasuryService.SweepRunResult result = treasury.runSweeps(merchantId);

        assertThat(result.evaluated()).isEqualTo(1);
        assertThat(result.fired()).isEqualTo(1);
        assertThat(result.totalSweptMinor()).isEqualTo(100_000L);

        // Money moved settlement -> bank; the ledger stays balanced (postEntry enforces it).
        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "bank")).isEqualTo(100_000L);

        List<SweepExecution> execs = treasury.listExecutions(merchantId);
        assertThat(execs).hasSize(1);
        SweepExecution exec = execs.get(0);
        assertThat(exec.getRuleId()).isEqualTo(rule.getId());
        assertThat(exec.getAmountMinor()).isEqualTo(100_000L);
        assertThat(exec.getSourceBalanceBeforeMinor()).isEqualTo(100_000L);
        assertThat(exec.getLedgerEntryId()).isNotNull();
    }

    @Test
    void keepMinorBufferIsRetained() {
        UUID merchantId = newMerchant();
        seedSettlement(merchantId, 100_000L);

        treasury.createRule(
                merchantId, "sweep-keep-buffer", "settlement", "bank",
                SweepTrigger.THRESHOLD, 50_000L, null, 20_000L); // retain ₹200.00

        TreasuryService.SweepRunResult result = treasury.runSweeps(merchantId);

        assertThat(result.fired()).isEqualTo(1);
        assertThat(result.totalSweptMinor()).isEqualTo(80_000L);
        assertThat(balance(merchantId, "settlement")).isEqualTo(20_000L); // buffer retained
        assertThat(balance(merchantId, "bank")).isEqualTo(80_000L);
        assertThat(treasury.listExecutions(merchantId)).singleElement()
                .extracting(SweepExecution::getAmountMinor)
                .isEqualTo(80_000L);
    }

    @Test
    void pausedRuleDoesNotFire() {
        UUID merchantId = newMerchant();
        seedSettlement(merchantId, 100_000L);

        SweepRule rule =
                treasury.createRule(
                        merchantId, "paused-sweep", "settlement", "bank",
                        SweepTrigger.THRESHOLD, 50_000L, null, 0L);
        treasury.pauseRule(merchantId, rule.getId());

        TreasuryService.SweepRunResult result = treasury.runSweeps(merchantId);

        assertThat(result.evaluated()).isZero(); // paused rules are not even evaluated
        assertThat(result.fired()).isZero();
        assertThat(balance(merchantId, "settlement")).isEqualTo(100_000L); // untouched
        assertThat(balance(merchantId, "bank")).isZero();
        assertThat(treasury.listExecutions(merchantId)).isEmpty();

        // Resuming lets it fire on the next pass.
        treasury.resumeRule(merchantId, rule.getId());
        assertThat(treasury.runSweeps(merchantId).fired()).isEqualTo(1);
        assertThat(balance(merchantId, "bank")).isEqualTo(100_000L);
    }

    @Test
    void belowThresholdDoesNotFire() {
        UUID merchantId = newMerchant();
        seedSettlement(merchantId, 40_000L); // below the 50,000 threshold

        treasury.createRule(
                merchantId, "no-fire", "settlement", "bank",
                SweepTrigger.THRESHOLD, 50_000L, null, 0L);

        TreasuryService.SweepRunResult result = treasury.runSweeps(merchantId);

        assertThat(result.evaluated()).isEqualTo(1);
        assertThat(result.fired()).isZero();
        assertThat(balance(merchantId, "settlement")).isEqualTo(40_000L);
    }

    /** Seeds a positive settlement balance: debit settlement / credit revenue. */
    private void seedSettlement(UUID merchantId, long amountMinor) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        ledger.postEntry(
                merchantId, "treasury-test seed", "INR",
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                        new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));
    }

    private UUID newMerchant() {
        return merchants.create("trs-" + UUID.randomUUID().toString().substring(0, 8), "Treasury Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
