package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure scoring: rank by auth-rate vs. cost, exclude DOWN providers, penalise DEGRADED ones. */
class ProviderScorerTest {

    private final UUID merchant = UUID.randomUUID();

    @Test
    void higherAuthRateWins() {
        ProviderScorecard good = card("A", 90, 10, 0);   // 90% auth
        ProviderScorecard poor = card("B", 50, 50, 0);   // 50% auth

        assertThat(ProviderScorer.best(List.of(good, poor), ProviderScorer.Weights.DEFAULT))
                .get()
                .extracting(ProviderScorecard::getProvider)
                .isEqualTo("A");
    }

    @Test
    void cheaperWinsWhenAuthRatesTie() {
        ProviderScorecard cheap = card("A", 80, 20, 30);   // 0.30% cost
        ProviderScorecard pricey = card("B", 80, 20, 175); // 1.75% cost

        assertThat(ProviderScorer.best(List.of(cheap, pricey), ProviderScorer.Weights.DEFAULT))
                .get()
                .extracting(ProviderScorecard::getProvider)
                .isEqualTo("A");
    }

    @Test
    void downProviderIsExcluded() {
        ProviderScorecard down = failNTimes(card("A", 100, 0, 0), ProviderScorecard.DOWN_THRESHOLD);
        ProviderScorecard ok = card("B", 60, 40, 0);

        assertThat(down.getHealth()).isEqualTo(ProviderHealth.DOWN);
        assertThat(ProviderScorer.best(List.of(down, ok), ProviderScorer.Weights.DEFAULT))
                .get()
                .extracting(ProviderScorecard::getProvider)
                .isEqualTo("B");
    }

    @Test
    void noDataYieldsEmptySoCallerFallsBack() {
        assertThat(ProviderScorer.best(List.of(new ProviderScorecard(merchant, "A")),
                        ProviderScorer.Weights.DEFAULT))
                .isEmpty();
    }

    /** Builds a scorecard with a given success/failure history and cost. */
    private ProviderScorecard card(String provider, int successes, int failures, int costBps) {
        ProviderScorecard c = new ProviderScorecard(merchant, provider);
        for (int i = 0; i < successes; i++) c.recordSuccess();
        for (int i = 0; i < failures; i++) c.recordFailure();
        // a trailing success clears any accidental DEGRADED/DOWN from the failure run above
        if (successes > 0) c.recordSuccess();
        c.setCostBps(costBps);
        return c;
    }

    private ProviderScorecard failNTimes(ProviderScorecard c, int n) {
        for (int i = 0; i < n; i++) c.recordFailure();
        return c;
    }
}
