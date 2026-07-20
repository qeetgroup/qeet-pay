package com.qeetgroup.qeetpay.treasury;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Treasury-automation API (PRD Novel N3): manage auto-sweep rules, run them (evaluate + execute), read
 * the append-only execution history, and get an advisory idle-cash recommendation.
 */
@Tag(
        name = "Treasury",
        description =
                "Programmable money — auto-sweep rules that move idle cash between ledger accounts, plus an advisory idle-cash recommendation.")
@RestController
@RequestMapping("/v1/treasury")
public class TreasuryController {

    private final TreasuryService treasury;

    public TreasuryController(TreasuryService treasury) {
        this.treasury = treasury;
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    @PostMapping("/rules")
    public ResponseEntity<RuleView> createRule(@Valid @RequestBody CreateRuleRequest req) {
        SweepRule rule =
                treasury.createRule(
                        MerchantContext.require(),
                        req.name(),
                        req.sourceAccountCode(),
                        req.targetAccountCode(),
                        req.trigger(),
                        req.thresholdMinor(),
                        req.schedule(),
                        req.keepMinor() == null ? 0L : req.keepMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(RuleView.of(rule));
    }

    @GetMapping("/rules")
    public List<RuleView> listRules() {
        return treasury.listRules(MerchantContext.require()).stream().map(RuleView::of).toList();
    }

    @GetMapping("/rules/{ruleId}")
    public RuleView getRule(@PathVariable UUID ruleId) {
        return RuleView.of(treasury.getRule(MerchantContext.require(), ruleId));
    }

    @PostMapping("/rules/{ruleId}/pause")
    public RuleView pauseRule(@PathVariable UUID ruleId) {
        return RuleView.of(treasury.pauseRule(MerchantContext.require(), ruleId));
    }

    @PostMapping("/rules/{ruleId}/resume")
    public RuleView resumeRule(@PathVariable UUID ruleId) {
        return RuleView.of(treasury.resumeRule(MerchantContext.require(), ruleId));
    }

    // ── Sweeps ────────────────────────────────────────────────────────────────

    @PostMapping("/sweeps/run")
    public SweepRunView runSweeps() {
        return SweepRunView.of(treasury.runSweeps(MerchantContext.require()));
    }

    @GetMapping("/sweeps")
    public List<SweepExecutionView> listSweeps() {
        return treasury.listExecutions(MerchantContext.require()).stream()
                .map(SweepExecutionView::of)
                .toList();
    }

    // ── Idle-cash recommendation ────────────────────────────────────────────────

    @GetMapping("/recommendations")
    public TreasuryService.IdleCashRecommendation recommendations(
            @RequestParam(defaultValue = "30") int horizonDays,
            @RequestParam(defaultValue = "30") int windowDays) {
        return treasury.recommend(MerchantContext.require(), horizonDays, windowDays);
    }

    // ── Records ──────────────────────────────────────────────────────────────

    @Schema(name = "TreasuryCreateRuleRequest")
    public record CreateRuleRequest(
            @NotBlank String name,
            @NotBlank String sourceAccountCode,
            @NotBlank String targetAccountCode,
            @NotNull SweepTrigger trigger,
            @PositiveOrZero Long thresholdMinor,
            String schedule,
            @PositiveOrZero Long keepMinor) {}

    @Schema(name = "TreasuryRuleView")
    public record RuleView(
            String id,
            String name,
            String sourceAccountCode,
            String targetAccountCode,
            String trigger,
            Long thresholdMinor,
            String schedule,
            long keepMinor,
            String currency,
            String status,
            Instant createdAt,
            Instant updatedAt) {
        static RuleView of(SweepRule r) {
            return new RuleView(
                    r.getId().toString(),
                    r.getName(),
                    r.getSourceAccountCode(),
                    r.getTargetAccountCode(),
                    r.getTrigger().name(),
                    r.getThresholdMinor(),
                    r.getSchedule(),
                    r.getKeepMinor(),
                    r.getCurrency(),
                    r.getStatus().name(),
                    r.getCreatedAt(),
                    r.getUpdatedAt());
        }
    }

    public record SweepExecutionView(
            String id,
            String ruleId,
            long amountMinor,
            long sourceBalanceBeforeMinor,
            String ledgerEntryId,
            Instant createdAt) {
        static SweepExecutionView of(SweepExecution e) {
            return new SweepExecutionView(
                    e.getId().toString(),
                    e.getRuleId().toString(),
                    e.getAmountMinor(),
                    e.getSourceBalanceBeforeMinor(),
                    e.getLedgerEntryId().toString(),
                    e.getCreatedAt());
        }
    }

    public record SweepRunView(
            int evaluated, int fired, long totalSweptMinor, List<SweepExecutionView> executions) {
        static SweepRunView of(TreasuryService.SweepRunResult r) {
            return new SweepRunView(
                    r.evaluated(),
                    r.fired(),
                    r.totalSweptMinor(),
                    r.executions().stream().map(SweepExecutionView::of).toList());
        }
    }
}
