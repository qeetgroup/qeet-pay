package com.qeetgroup.qeetpay.payments;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Ranks acquirers by their scorecards (PRD Module 07.3). Pure + deterministic — no Spring/DB — so it
 * is unit-testable in isolation. The score trades authorization rate against cost; a DOWN provider is
 * excluded outright, and a DEGRADED one carries a fixed penalty. This is the seam an ML model can
 * replace later without touching the router.
 */
public final class ProviderScorer {

    /** Relative weights: how much authorization rate vs. cost matters. */
    public record Weights(double authRate, double cost) {
        public static final Weights DEFAULT = new Weights(1.0, 0.5);
    }

    private static final double DEGRADED_PENALTY = 0.1;

    private ProviderScorer() {}

    /** Higher is better. A DOWN provider scores −∞ (never chosen while any alternative exists). */
    public static double score(ProviderScorecard sc, Weights w) {
        if (sc.getHealth() == ProviderHealth.DOWN) {
            return Double.NEGATIVE_INFINITY;
        }
        double base = w.authRate() * sc.authRate() - w.cost() * (sc.getCostBps() / 10_000.0);
        return sc.getHealth() == ProviderHealth.DEGRADED ? base - DEGRADED_PENALTY : base;
    }

    /**
     * The best scorecard among those with real data (attempts &gt; 0) and not DOWN. Empty when there
     * is nothing to go on — the caller then falls back to its default provider preference.
     */
    public static Optional<ProviderScorecard> best(Collection<ProviderScorecard> cards, Weights w) {
        return cards.stream()
                .filter(c -> c.getHealth() != ProviderHealth.DOWN)
                .filter(c -> c.getAttempts() > 0)
                .max(Comparator.comparingDouble(c -> score(c, w)));
    }
}
