package com.qeetgroup.qeetpay.dunning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit test — no Spring context, no DB. */
class DunningRuleEngineTest {

    private final DunningRuleEngine engine = new DunningRuleEngine();

    @Test
    void catchAllRuleMatchesAnyCode() {
        DunningRule rule = rule("*", 24, 3);
        Optional<DunningRule> match = engine.match(List.of(rule), "card_declined");
        assertThat(match).contains(rule);
    }

    @Test
    void literalPatternMatchesExact() {
        DunningRule rule = rule("insufficient_funds", 12, 3);
        assertThat(engine.match(List.of(rule), "insufficient_funds")).contains(rule);
        assertThat(engine.match(List.of(rule), "card_declined")).isEmpty();
    }

    @Test
    void firstMatchingRuleWins() {
        DunningRule specific = rule("insufficient_funds", 6, 2);
        DunningRule catchAll = rule("*", 24, 3);
        Optional<DunningRule> match = engine.match(List.of(specific, catchAll), "insufficient_funds");
        assertThat(match).contains(specific);
    }

    @Test
    void inactiveRuleIsSkipped() {
        DunningRule active = rule("*", 24, 3);
        DunningRule inactive = rule("*", 6, 1);
        inactive.deactivate();
        Optional<DunningRule> match = engine.match(List.of(inactive, active), "any_code");
        assertThat(match).contains(active);
    }

    @Test
    void exhaustedWhenAttemptsReachMax() {
        DunningRule rule = rule("*", 24, 3);
        assertThat(engine.isExhausted(rule, 2)).isFalse();
        assertThat(engine.isExhausted(rule, 3)).isTrue();
        assertThat(engine.isExhausted(rule, 4)).isTrue();
    }

    @Test
    void nextRetryIsInFuture() {
        DunningRule rule = rule("*", 24, 3);
        assertThat(engine.nextRetryAt(rule)).isAfter(java.time.Instant.now());
    }

    private static DunningRule rule(String pattern, int intervalHours, int maxAttempts) {
        return new DunningRule(UUID.randomUUID(), "test-rule", pattern, intervalHours, maxAttempts, null);
    }
}
