package com.qeetgroup.qeetpay.dunning;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pure rule-matching logic — no DB, no transactions. Given a failure code and an ordered list
 * of active rules, returns the first rule that matches. Also computes the next retry timestamp.
 */
@Component
public class DunningRuleEngine {

    /** Find the first active rule that matches the given failure code. */
    public Optional<DunningRule> match(List<DunningRule> rules, String failureCode) {
        return rules.stream()
                .filter(DunningRule::isActive)
                .filter(r -> r.matches(failureCode))
                .findFirst();
    }

    /** Next retry scheduled at now + rule.retryIntervalHours. */
    public Instant nextRetryAt(DunningRule rule) {
        return Instant.now().plus(rule.getRetryIntervalHours(), ChronoUnit.HOURS);
    }

    /** True if no more retries remain (attempt count has reached maxAttempts). */
    public boolean isExhausted(DunningRule rule, int attemptsSoFar) {
        return attemptsSoFar >= rule.getMaxAttempts();
    }
}
