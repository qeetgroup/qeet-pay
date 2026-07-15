package com.qeetgroup.qeetpay.dunning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.billing.BillingService;
import com.qeetgroup.qeetpay.billing.Subscription;
import com.qeetgroup.qeetpay.billing.SubscriptionStatus;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dunning orchestrator (TAD Module 04).
 * <p>
 * {@link #trigger(UUID, UUID, String)} is called when a mandate/payment fails. It picks the
 * matching rule, records attempt #1 as FAILED, and schedules the next retry time.
 * {@link #executeRetry(UUID, UUID)} is called by the scheduler when a retry is due.
 * On exhaustion the subscription is CANCELLED.
 */
@Service
public class DunningService {

    private final DunningRuleRepository rules;
    private final DunningAttemptRepository attempts;
    private final DunningRuleEngine engine;
    private final FailureClassifier classifier;
    private final BillingService billing;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public DunningService(
            DunningRuleRepository rules,
            DunningAttemptRepository attempts,
            DunningRuleEngine engine,
            FailureClassifier classifier,
            BillingService billing,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.rules = rules;
        this.attempts = attempts;
        this.engine = engine;
        this.classifier = classifier;
        this.billing = billing;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DunningRule createRule(UUID merchantId, String name, String failureCodePattern,
            int retryIntervalHours, int maxAttempts, String notifyChannels) {
        merchantScope.apply(merchantId);
        return rules.save(new DunningRule(merchantId, name, failureCodePattern,
                retryIntervalHours, maxAttempts, notifyChannels));
    }

    @Transactional(readOnly = true)
    public List<DunningRule> listRules(UUID merchantId) {
        merchantScope.apply(merchantId);
        return rules.findByMerchantIdAndActiveTrue(merchantId);
    }

    @Transactional(readOnly = true)
    public List<DunningAttempt> attemptsFor(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        return attempts.findBySubscriptionIdOrderByAttemptNumberAsc(subscriptionId);
    }

    /**
     * Called when a payment fails. Marks subscription PAST_DUE, matches a dunning rule,
     * records attempt #1 as FAILED, and schedules the next retry (or cancels if exhausted).
     */
    @Transactional
    public Optional<DunningAttempt> trigger(UUID merchantId, UUID subscriptionId, String failureCode) {
        merchantScope.apply(merchantId);
        Subscription sub = billing.getSubscription(merchantId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) return Optional.empty();

        billing.markPastDue(merchantId, subscriptionId);

        List<DunningRule> merchantRules = rules.findByMerchantIdAndActiveTrue(merchantId);
        Optional<DunningRule> matchedRule = engine.match(merchantRules, failureCode);
        if (matchedRule.isEmpty()) return Optional.empty();

        DunningRule rule = matchedRule.get();
        DunningAttempt attempt = new DunningAttempt(merchantId, subscriptionId, rule.getId(), 1, Instant.now());
        attempt.recordResult(DunningAttempt.FAILED, failureCode);
        attempts.save(attempt);

        if (!engine.isExhausted(rule, 1)) {
            scheduleNextRetry(merchantId, subscriptionId, rule, 2);
        } else {
            cancelDunned(merchantId, subscriptionId);
        }

        outbox.enqueue(merchantId, "dunning.triggered", json("subscriptionId", subscriptionId, "rule", rule.getId()));
        return Optional.of(attempt);
    }

    /**
     * Explainable classification of a failure code (PRD Module 04.1) — no state change. Returns the
     * category, whether to auto-retry, the adaptive delay + channels, and a plain-English rationale.
     */
    public RetryRecommendation classify(String failureCode) {
        return classifier.classify(failureCode);
    }

    /**
     * AI-dunning entrypoint (PRD Module 04.1): like {@link #trigger} but the failure code is
     * classified and the classification drives the next retry's timing and notification channels
     * (instead of a flat rule interval). Non-retryable categories (risk/mandate) stop auto-recovery
     * and escalate. The classification is recorded on the attempt for the merchant to inspect.
     */
    @Transactional
    public DunningAttempt triggerSmart(UUID merchantId, UUID subscriptionId, String failureCode) {
        merchantScope.apply(merchantId);
        Subscription sub = billing.getSubscription(merchantId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("subscription is not ACTIVE; status=" + sub.getStatus());
        }
        billing.markPastDue(merchantId, subscriptionId);

        RetryRecommendation rec = classifier.classify(failureCode);
        UUID ruleId =
                engine.match(rules.findByMerchantIdAndActiveTrue(merchantId), failureCode)
                        .map(DunningRule::getId)
                        .orElse(null);

        DunningAttempt attempt = new DunningAttempt(merchantId, subscriptionId, ruleId, 1, Instant.now());
        attempt.recordResult(DunningAttempt.FAILED, failureCode);
        attempt.applyClassification(rec);
        attempts.save(attempt);

        if (rec.retryable()) {
            Instant nextRetry = Instant.now().plus(rec.recommendedDelayHours(), ChronoUnit.HOURS);
            DunningAttempt scheduled = new DunningAttempt(merchantId, subscriptionId, ruleId, 2, nextRetry);
            scheduled.applyClassification(rec);
            attempts.save(scheduled);
        } else {
            cancelDunned(merchantId, subscriptionId); // risk / mandate → stop auto-retry, escalate
        }

        outbox.enqueue(merchantId, "dunning.classified", classifiedJson(subscriptionId, rec));
        return attempt;
    }

    /**
     * Called by the scheduler when a scheduled retry is due.
     * Simulates a payment attempt (always succeeds in sandbox — real payment via MandateService in prod).
     * Returns the attempt record with result SUCCESS or FAILED.
     */
    @Transactional
    public DunningAttempt executeRetry(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        Subscription sub = billing.getSubscription(merchantId, subscriptionId);
        if (sub.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new IllegalStateException("subscription is not PAST_DUE; status=" + sub.getStatus());
        }

        List<DunningRule> merchantRules = rules.findByMerchantIdAndActiveTrue(merchantId);
        Optional<DunningRule> matchedRule = engine.match(merchantRules, "*");
        if (matchedRule.isEmpty()) throw new DunningNotFoundException("no active dunning rule for merchant " + merchantId);

        DunningRule rule = matchedRule.get();
        int attemptsSoFar = attempts.countBySubscriptionIdAndResult(subscriptionId, DunningAttempt.FAILED);
        int nextAttemptNumber = attemptsSoFar + 1;

        DunningAttempt attempt = new DunningAttempt(merchantId, subscriptionId, rule.getId(), nextAttemptNumber, Instant.now());

        // Sandbox: retry always fails until exhausted, then succeeds once recovered externally.
        // Real implementation would delegate to MandateService.debit().
        if (engine.isExhausted(rule, nextAttemptNumber)) {
            attempt.recordResult(DunningAttempt.FAILED, "exhausted");
            cancelDunned(merchantId, subscriptionId);
            outbox.enqueue(merchantId, "dunning.exhausted", json("subscriptionId", subscriptionId, "rule", rule.getId()));
        } else {
            attempt.recordResult(DunningAttempt.FAILED, "payment_failed");
            scheduleNextRetry(merchantId, subscriptionId, rule, nextAttemptNumber + 1);
        }

        return attempts.save(attempt);
    }

    @Transactional
    public void scheduleRetryForPastDue(UUID merchantId, UUID subscriptionId, int attemptNumber) {
        merchantScope.apply(merchantId);
        List<DunningRule> merchantRules = rules.findByMerchantIdAndActiveTrue(merchantId);
        Optional<DunningRule> rule = engine.match(merchantRules, "*");
        rule.ifPresent(r -> scheduleNextRetry(merchantId, subscriptionId, r, attemptNumber));
    }

    private void scheduleNextRetry(UUID merchantId, UUID subscriptionId, DunningRule rule, int attemptNumber) {
        Instant nextRetry = engine.nextRetryAt(rule);
        DunningAttempt scheduled = new DunningAttempt(merchantId, subscriptionId, rule.getId(), attemptNumber, nextRetry);
        attempts.save(scheduled);
    }

    private void cancelDunned(UUID merchantId, UUID subscriptionId) {
        billing.cancelSubscription(merchantId, subscriptionId, false);
    }

    private String json(String k1, UUID v1, String k2, UUID v2) {
        try {
            return objectMapper.writeValueAsString(Map.of(k1, v1.toString(), k2, v2.toString()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("dunning event serialisation failed", e);
        }
    }

    private String classifiedJson(UUID subscriptionId, RetryRecommendation rec) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("subscriptionId", subscriptionId.toString());
        b.put("category", rec.category().name());
        b.put("retryable", rec.retryable());
        b.put("recommendedDelayHours", rec.recommendedDelayHours());
        b.put("recommendedChannels", rec.recommendedChannels());
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("dunning event serialisation failed", e);
        }
    }
}
