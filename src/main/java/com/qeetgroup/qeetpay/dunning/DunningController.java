package com.qeetgroup.qeetpay.dunning;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RestController;

/** Dunning API (TAD Module 04): configure retry rules; view attempt history. */
@RestController
@RequestMapping("/v1/dunning")
public class DunningController {

    private final DunningService dunning;

    public DunningController(DunningService dunning) {
        this.dunning = dunning;
    }

    @PostMapping("/rules")
    public ResponseEntity<RuleView> createRule(@Valid @RequestBody CreateRuleRequest req) {
        DunningRule rule = dunning.createRule(
                MerchantContext.require(), req.name(), req.failureCodePattern(),
                req.retryIntervalHours(), req.maxAttempts(), req.notifyChannels());
        return ResponseEntity.status(HttpStatus.CREATED).body(RuleView.of(rule));
    }

    @GetMapping("/rules")
    public List<RuleView> listRules() {
        return dunning.listRules(MerchantContext.require()).stream().map(RuleView::of).toList();
    }

    @GetMapping("/subscriptions/{subscriptionId}/attempts")
    public List<AttemptView> attempts(@PathVariable UUID subscriptionId) {
        return dunning.attemptsFor(MerchantContext.require(), subscriptionId)
                .stream().map(AttemptView::of).toList();
    }

    /** Explainable UPI-failure classification (PRD Module 04.1) — no state change. */
    @PostMapping("/classify")
    public ClassificationView classify(@Valid @RequestBody ClassifyRequest req) {
        return ClassificationView.of(dunning.classify(req.failureCode()));
    }

    /** AI-dunning trigger: classify the failure and schedule an adaptive retry accordingly. */
    @PostMapping("/subscriptions/{subscriptionId}/trigger-smart")
    public AttemptView triggerSmart(
            @PathVariable UUID subscriptionId, @Valid @RequestBody ClassifyRequest req) {
        return AttemptView.of(
                dunning.triggerSmart(MerchantContext.require(), subscriptionId, req.failureCode()));
    }

    // ── Request / view records ────────────────────────────────────────────────

    public record ClassifyRequest(@NotBlank String failureCode) {}

    public record ClassificationView(
            String category, boolean retryable, int recommendedDelayHours,
            String recommendedChannels, String rationale) {
        static ClassificationView of(RetryRecommendation r) {
            return new ClassificationView(
                    r.category().name(), r.retryable(), r.recommendedDelayHours(),
                    r.recommendedChannels(), r.rationale());
        }
    }

    public record CreateRuleRequest(
            @NotBlank String name,
            String failureCodePattern,
            @Min(1) int retryIntervalHours,
            @Min(1) int maxAttempts,
            String notifyChannels) {}

    public record RuleView(String id, String name, String failureCodePattern,
            int retryIntervalHours, int maxAttempts, String notifyChannels, boolean active) {
        static RuleView of(DunningRule r) {
            return new RuleView(r.getId().toString(), r.getName(), r.getFailureCodePattern(),
                    r.getRetryIntervalHours(), r.getMaxAttempts(), r.getNotifyChannels(), r.isActive());
        }
    }

    public record AttemptView(String id, String subscriptionId, int attemptNumber,
            Instant scheduledAt, Instant attemptedAt, String result, String failureReason,
            String failureCategory, Integer recommendedDelayHours, String recommendedChannels,
            String classificationRationale) {
        static AttemptView of(DunningAttempt a) {
            return new AttemptView(a.getId().toString(), a.getSubscriptionId().toString(),
                    a.getAttemptNumber(), a.getScheduledAt(), a.getAttemptedAt(),
                    a.getResult(), a.getFailureReason(),
                    a.getFailureCategory(), a.getRecommendedDelayHours(), a.getRecommendedChannels(),
                    a.getClassificationRationale());
        }
    }
}
