package com.qeetgroup.qeetpay.treasury;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.analytics.CashFlowForecastService;
import com.qeetgroup.qeetpay.ledger.Account;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programmable treasury automation (PRD Novel N3). Merchants register {@link SweepRule}s that move
 * idle cash between their own ledger accounts; {@link #runSweeps} evaluates the active rules against
 * live ledger balances and, for each rule that fires, posts a <b>balanced</b> sweep entry, records an
 * append-only {@link SweepExecution}, and emits {@code treasury.sweep.executed} to the outbox. The
 * idle-cash {@linkplain #recommend recommendation} is advisory and read-only — it composes the
 * analytics cash-flow forecast; it never posts.
 */
@Service
public class TreasuryService {

    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final SweepRuleRepository rules;
    private final SweepExecutionRepository executions;
    private final LedgerService ledger;
    private final CashFlowForecastService forecast;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public TreasuryService(
            SweepRuleRepository rules,
            SweepExecutionRepository executions,
            LedgerService ledger,
            CashFlowForecastService forecast,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.rules = rules;
        this.executions = executions;
        this.ledger = ledger;
        this.forecast = forecast;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    /** Creates a sweep rule. Validates the trigger fields and that source/target accounts exist. */
    @Transactional
    public SweepRule createRule(
            UUID merchantId,
            String name,
            String sourceAccountCode,
            String targetAccountCode,
            SweepTrigger trigger,
            Long thresholdMinor,
            String schedule,
            long keepMinor) {
        merchantScope.apply(merchantId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (sourceAccountCode == null || sourceAccountCode.isBlank()
                || targetAccountCode == null || targetAccountCode.isBlank()) {
            throw new IllegalArgumentException("sourceAccountCode and targetAccountCode are required");
        }
        if (sourceAccountCode.equals(targetAccountCode)) {
            throw new IllegalArgumentException("source and target accounts must differ");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger is required");
        }
        if (keepMinor < 0) {
            throw new IllegalArgumentException("keepMinor must not be negative");
        }
        if (trigger == SweepTrigger.THRESHOLD) {
            if (thresholdMinor == null || thresholdMinor <= 0) {
                throw new IllegalArgumentException("thresholdMinor is required (>0) for a THRESHOLD rule");
            }
            schedule = null;
        } else {
            if (schedule == null || schedule.isBlank()) {
                throw new IllegalArgumentException("schedule is required for a SCHEDULE rule");
            }
            thresholdMinor = null;
        }

        // Resolve accounts (also validates they belong to the merchant) and lock in the currency.
        Account source = ledger.accountByCode(merchantId, sourceAccountCode);
        Account target = ledger.accountByCode(merchantId, targetAccountCode);
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new IllegalArgumentException("source and target accounts must share a currency");
        }

        return rules.save(
                new SweepRule(
                        merchantId, name, sourceAccountCode, targetAccountCode, trigger,
                        thresholdMinor, schedule, keepMinor, source.getCurrency()));
    }

    @Transactional(readOnly = true)
    public List<SweepRule> listRules(UUID merchantId) {
        merchantScope.apply(merchantId);
        return rules.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public SweepRule getRule(UUID merchantId, UUID ruleId) {
        merchantScope.apply(merchantId);
        return load(merchantId, ruleId);
    }

    @Transactional
    public SweepRule pauseRule(UUID merchantId, UUID ruleId) {
        merchantScope.apply(merchantId);
        SweepRule rule = load(merchantId, ruleId);
        rule.pause();
        return rules.save(rule);
    }

    @Transactional
    public SweepRule resumeRule(UUID merchantId, UUID ruleId) {
        merchantScope.apply(merchantId);
        SweepRule rule = load(merchantId, ruleId);
        rule.resume();
        return rules.save(rule);
    }

    // ── Execution ────────────────────────────────────────────────────────────

    /**
     * Evaluates every ACTIVE rule against current ledger balances and executes the ones that fire.
     * A THRESHOLD rule fires when its source balance exceeds {@code thresholdMinor}; a SCHEDULE rule
     * fires on the pass whenever the source still holds more than its {@code keepMinor} buffer. Each
     * firing sweeps {@code sourceBalance − keepMinor} in one balanced entry.
     */
    @Transactional
    public SweepRunResult runSweeps(UUID merchantId) {
        merchantScope.apply(merchantId);
        List<SweepRule> active =
                rules.findByMerchantIdAndStatusOrderByCreatedAt(merchantId, SweepRuleStatus.ACTIVE);

        List<SweepExecution> fired = new ArrayList<>();
        long totalSwept = 0;
        for (SweepRule rule : active) {
            SweepExecution exec = evaluateAndExecute(merchantId, rule);
            if (exec != null) {
                fired.add(exec);
                totalSwept += exec.getAmountMinor();
            }
        }
        return new SweepRunResult(active.size(), fired.size(), totalSwept, fired);
    }

    private SweepExecution evaluateAndExecute(UUID merchantId, SweepRule rule) {
        Account source = ledger.accountByCode(merchantId, rule.getSourceAccountCode());
        long balance = ledger.balanceMinor(merchantId, source.getId());

        boolean triggered =
                rule.getTrigger() == SweepTrigger.SCHEDULE
                        || (rule.getThresholdMinor() != null && balance > rule.getThresholdMinor());
        long sweepAmount = balance - rule.getKeepMinor();
        if (!triggered || sweepAmount <= 0) {
            return null;
        }

        Account target = ledger.accountByCode(merchantId, rule.getTargetAccountCode());
        List<LedgerLineInput> lineInputs;
        if (source.getType().normalSide() == Direction.DEBIT) {
            // Asset-like source (e.g. settlement, bank): credit source to reduce it, debit target.
            lineInputs =
                    List.of(
                            new LedgerLineInput(target.getId(), Direction.DEBIT, sweepAmount),
                            new LedgerLineInput(source.getId(), Direction.CREDIT, sweepAmount));
        } else {
            // Liability-like source: debit source to reduce it, credit target.
            lineInputs =
                    List.of(
                            new LedgerLineInput(source.getId(), Direction.DEBIT, sweepAmount),
                            new LedgerLineInput(target.getId(), Direction.CREDIT, sweepAmount));
        }

        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "treasury sweep " + rule.getName() + " ("
                                + rule.getSourceAccountCode() + "->" + rule.getTargetAccountCode() + ")",
                        rule.getCurrency(),
                        lineInputs);

        SweepExecution exec =
                executions.save(
                        new SweepExecution(rule.getId(), merchantId, sweepAmount, balance, entryId));
        outbox.enqueue(merchantId, "treasury.sweep.executed", executionJson(rule, exec));
        return exec;
    }

    @Transactional(readOnly = true)
    public List<SweepExecution> listExecutions(UUID merchantId) {
        merchantScope.apply(merchantId);
        return executions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    // ── Idle-cash recommendation (advisory, read-only) ─────────────────────────

    /**
     * Advisory idle-cash optimization: reads the {@code settlement} balance and the analytics
     * cash-flow forecast, estimates a working-capital buffer to retain, and reports how much idle cash
     * could be swept/invested. Posts nothing.
     */
    @Transactional(readOnly = true)
    public IdleCashRecommendation recommend(UUID merchantId, int horizonDays, int windowDays) {
        merchantScope.apply(merchantId);
        long settlement =
                ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "settlement").getId());
        CashFlowForecastService.CashFlowForecast fc =
                forecast.forecast(merchantId, horizonDays, windowDays);

        // Retain enough to cover a projected drawdown over the horizon; otherwise a light buffer.
        long suggestedBuffer =
                fc.avgDailyNetMinor() < 0 ? -fc.avgDailyNetMinor() * horizonDays : 0;
        long idle = Math.max(0, settlement - suggestedBuffer);

        String recommendation;
        if (idle <= 0) {
            recommendation =
                    "No idle cash to optimize — the settlement balance is within the buffer needed to"
                            + " cover the projected cash-flow drawdown.";
        } else if (fc.avgDailyNetMinor() < 0) {
            recommendation =
                    "About " + idle + " minor units are idle above the working-capital buffer; sweep the"
                            + " surplus while retaining the buffer, as recent net cash-flow is negative.";
        } else {
            recommendation =
                    "About " + idle + " minor units are idle in settlement; sweep to an interest-bearing"
                            + " account — the projected cash-flow trajectory is positive.";
        }

        return new IdleCashRecommendation(
                settlement,
                suggestedBuffer,
                idle,
                fc.avgDailyNetMinor(),
                fc.projectedEndBalanceMinor(),
                horizonDays,
                recommendation);
    }

    /** Convenience overload using the default 30-day horizon/window. */
    @Transactional(readOnly = true)
    public IdleCashRecommendation recommend(UUID merchantId) {
        return recommend(merchantId, DEFAULT_HORIZON_DAYS, DEFAULT_WINDOW_DAYS);
    }

    // ── Helpers / result types ─────────────────────────────────────────────────

    private SweepRule load(UUID merchantId, UUID ruleId) {
        return rules.findById(ruleId)
                .filter(r -> r.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new SweepRuleNotFoundException("no sweep rule " + ruleId));
    }

    private String executionJson(SweepRule rule, SweepExecution exec) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("ruleId", rule.getId().toString());
        b.put("ruleName", rule.getName());
        b.put("source", rule.getSourceAccountCode());
        b.put("target", rule.getTargetAccountCode());
        b.put("amountMinor", exec.getAmountMinor());
        b.put("ledgerEntryId", exec.getLedgerEntryId().toString());
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialise treasury sweep event", ex);
        }
    }

    /** Outcome of a {@link #runSweeps} pass. */
    public record SweepRunResult(
            int evaluated, int fired, long totalSweptMinor, List<SweepExecution> executions) {}

    /** Advisory idle-cash view built from the settlement balance + analytics cash-flow forecast. */
    public record IdleCashRecommendation(
            long settlementBalanceMinor,
            long suggestedBufferMinor,
            long idleCashMinor,
            long avgDailyNetMinor,
            long projectedEndBalanceMinor,
            int horizonDays,
            String recommendation) {}
}
