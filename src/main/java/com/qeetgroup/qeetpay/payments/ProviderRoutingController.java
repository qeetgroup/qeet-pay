package com.qeetgroup.qeetpay.payments;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    private final ProviderRoutingService routing;

    public ProviderRoutingController(ProviderRoutingService routing) {
        this.routing = routing;
    }

    @GetMapping("/scorecards")
    public List<ScorecardView> scorecards() {
        return routing.scorecards(MerchantContext.require()).stream().map(ScorecardView::of).toList();
    }

    @PutMapping("/{provider}/cost")
    public ScorecardView setCost(@PathVariable String provider, @Valid @RequestBody CostRequest req) {
        return ScorecardView.of(routing.setCost(MerchantContext.require(), provider, req.costBps()));
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
}
