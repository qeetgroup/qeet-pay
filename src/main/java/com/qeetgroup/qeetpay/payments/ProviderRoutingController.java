package com.qeetgroup.qeetpay.payments;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smart-orchestration API (PRD Module 07.3): inspect the per-provider routing scorecards (auth rate,
 * health, cost) and configure each provider's cost basis used when routing.
 */
@Tag(
        name = "Orchestration",
        description = "Smart provider routing — inspect per-provider scorecards (auth rate, health, cost) and set the cost basis.")
@RestController
@RequestMapping("/v1/payments/providers")
public class ProviderRoutingController {

    /** Default candidate acquirers to rank when none are supplied — mirrors {@code ProviderRouter} order. */
    private static final List<String> DEFAULT_CANDIDATES = List.of("RAZORPAY", "SANDBOX");

    private final ProviderRoutingService routing;
    private final AiProviderScorer aiScorer;
    private final ComplianceRouter compliance;

    public ProviderRoutingController(
            ProviderRoutingService routing, AiProviderScorer aiScorer, ComplianceRouter compliance) {
        this.routing = routing;
        this.aiScorer = aiScorer;
        this.compliance = compliance;
    }

    @GetMapping("/scorecards")
    public List<ScorecardView> scorecards() {
        return routing.scorecards(MerchantContext.require()).stream().map(ScorecardView::of).toList();
    }

    @PutMapping("/{provider}/cost")
    public ScorecardView setCost(@PathVariable String provider, @Valid @RequestBody CostRequest req) {
        return ScorecardView.of(routing.setCost(MerchantContext.require(), provider, req.costBps()));
    }

    /**
     * Explainable routing recommendation (PRD Module 07.3 "Orchestration ML" + 07.6 "Compliance-aware
     * routing"). Ranks the candidate acquirers by predicted auth-rate × (1 − cost) via the AI gateway
     * (deterministic scorecard fallback), layers the GST compliance assessment (GSTIN correctness +
     * place of supply) on top, and returns a plain-English explanation of why the top provider was
     * chosen. Read-only — the actual money-moving route selection stays deterministic.
     *
     * @param candidates optional comma-separated candidate acquirers (default RAZORPAY,SANDBOX)
     * @param gstin optional merchant GSTIN to validate for compliance
     * @param placeOfSupply optional 2-digit place-of-supply state code (buyer)
     */
    @GetMapping("/routing-explain")
    public RoutingExplainView routingExplain(
            @RequestParam(name = "candidates", required = false) List<String> candidates,
            @RequestParam(name = "gstin", required = false) String gstin,
            @RequestParam(name = "placeOfSupply", required = false) String placeOfSupply) {
        List<String> pick =
                (candidates == null || candidates.isEmpty()) ? DEFAULT_CANDIDATES : candidates;
        ComplianceAssessment assessment = compliance.assess(gstin, placeOfSupply);
        String context = compliance.contextString(gstin, assessment);
        ProviderRanking ranking =
                aiScorer.rank(MerchantContext.require(), pick, context, Set.of());
        return RoutingExplainView.of(ranking, assessment);
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CostRequest(@Min(0) @Max(10_000) int costBps) {}

    public record ScorecardView(
            String provider, long attempts, long successes, long failures, double authRate,
            int consecutiveFailures, int costBps, String health, Instant lastOutcomeAt) {
        static ScorecardView of(ProviderScorecard s) {
            return new ScorecardView(
                    s.getProvider(), s.getAttempts(), s.getSuccesses(), s.getFailures(), s.authRate(),
                    s.getConsecutiveFailures(), s.getCostBps(), s.getHealth().name(), s.getLastOutcomeAt());
        }
    }

    public record RankedProviderView(
            String provider, double predictedAuthRate, int costBps, String health, double score, String why) {
        static RankedProviderView of(RankedProvider r) {
            return new RankedProviderView(
                    r.provider(), r.predictedAuthRate(), r.costBps(), r.health(), r.score(), r.why());
        }
    }

    public record ComplianceView(
            boolean gstinPresent, boolean gstinValid, String supplierStateCode,
            String placeOfSupplyStateCode, String supplyType, boolean igstApplicable,
            boolean compliant, List<String> notes) {
        static ComplianceView of(ComplianceAssessment a) {
            return new ComplianceView(
                    a.gstinPresent(), a.gstinValid(), a.supplierStateCode(), a.placeOfSupplyStateCode(),
                    a.supplyType(), a.igstApplicable(), a.compliant(), a.notes());
        }
    }

    public record RoutingExplainView(
            List<RankedProviderView> ranking,
            String recommendedProvider,
            String method,
            boolean aiAssisted,
            String decisionId,
            ComplianceView compliance,
            String explanation) {
        static RoutingExplainView of(ProviderRanking ranking, ComplianceAssessment assessment) {
            List<RankedProviderView> views =
                    ranking.providers().stream().map(RankedProviderView::of).toList();
            return new RoutingExplainView(
                    views,
                    ranking.recommendedProvider(),
                    ranking.method(),
                    ranking.aiAssisted(),
                    ranking.decisionId() == null ? null : ranking.decisionId().toString(),
                    ComplianceView.of(assessment),
                    explain(ranking, assessment));
        }

        private static String explain(ProviderRanking ranking, ComplianceAssessment a) {
            StringBuilder sb = new StringBuilder();
            if (!a.compliant()) {
                sb.append("COMPLIANCE WARNING: ")
                        .append(a.notes().isEmpty() ? "GST compliance could not be verified." : a.notes().get(0))
                        .append(' ');
            }
            if (ranking.recommendedProvider() == null || ranking.providers().isEmpty()) {
                sb.append("No candidate acquirer available to route on.");
                return sb.toString();
            }
            RankedProvider top = ranking.providers().get(0);
            sb.append("Recommended ")
                    .append(top.provider())
                    .append(" — ")
                    .append(top.why())
                    .append(" [method: ")
                    .append(ranking.method())
                    .append(ranking.aiAssisted()
                            ? ", AI gateway"
                            : ", deterministic scorecard (AI model unavailable/low-confidence)")
                    .append(", audited decision ")
                    .append(ranking.decisionId())
                    .append("]. Compliance: supply ")
                    .append(a.supplyType())
                    .append(a.igstApplicable() ? " (IGST)" : "")
                    .append(", GSTIN ")
                    .append(!a.gstinPresent() ? "not supplied" : (a.gstinValid() ? "valid" : "INVALID"))
                    .append('.');
            return sb.toString();
        }
    }
}
