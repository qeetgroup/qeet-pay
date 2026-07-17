package com.qeetgroup.qeetpay.payments;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Smart-orchestration decisioning (PRD Module 07.3). Maintains a {@link ProviderScorecard} per
 * merchant+provider and answers two questions for the {@link ProviderRouter}: <em>which</em> acquirer
 * to use for the next call ({@link #chooseProviderName}) and — after the call — <em>how it went</em>
 * ({@link #recordOutcome}). Choice among the given candidates uses {@link ProviderScorer}; with no
 * data yet, it preserves the caller's default order (first candidate). All merchant-scoped via RLS.
 */
@Service
public class ProviderRoutingService {

    private final ProviderScorecardRepository scorecards;
    private final MerchantScope merchantScope;

    public ProviderRoutingService(ProviderScorecardRepository scorecards, MerchantScope merchantScope) {
        this.scorecards = scorecards;
        this.merchantScope = merchantScope;
    }

    /**
     * Picks the best-scoring healthy provider among {@code candidates} (order = default preference).
     * Falls back to the first candidate when no scorecard has data or all are DOWN.
     */
    @Transactional
    public String chooseProviderName(UUID merchantId, List<String> candidates) {
        merchantScope.apply(merchantId);
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one candidate provider is required");
        }
        Map<String, ProviderScorecard> byName =
                scorecards.findByMerchantId(merchantId).stream()
                        .filter(c -> candidates.contains(c.getProvider()))
                        .collect(Collectors.toMap(ProviderScorecard::getProvider, c -> c));

        Optional<ProviderScorecard> best =
                ProviderScorer.best(byName.values(), ProviderScorer.Weights.DEFAULT);
        if (best.isPresent()) {
            return best.get().getProvider();
        }
        // No provider has scored history yet — fall back to the first candidate that isn't known DOWN
        // (so we still route around an unhealthy preferred provider), else the default preference.
        for (String candidate : candidates) {
            ProviderScorecard card = byName.get(candidate);
            if (card == null || card.getHealth() != ProviderHealth.DOWN) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    /** Records the outcome of a provider call, updating the rolling rate + health signal. */
    @Transactional
    public ProviderScorecard recordOutcome(UUID merchantId, String provider, boolean success) {
        merchantScope.apply(merchantId);
        ProviderScorecard card =
                scorecards
                        .findByMerchantIdAndProvider(merchantId, provider)
                        .orElseGet(() -> new ProviderScorecard(merchantId, provider));
        if (success) {
            card.recordSuccess();
        } else {
            card.recordFailure();
        }
        return scorecards.save(card);
    }

    /** Sets the cost basis (basis points) used to trade cost against auth-rate when routing. */
    @Transactional
    public ProviderScorecard setCost(UUID merchantId, String provider, int costBps) {
        merchantScope.apply(merchantId);
        ProviderScorecard card =
                scorecards
                        .findByMerchantIdAndProvider(merchantId, provider)
                        .orElseGet(() -> new ProviderScorecard(merchantId, provider));
        card.setCostBps(costBps);
        return scorecards.save(card);
    }

    @Transactional(readOnly = true)
    public List<ProviderScorecard> scorecards(UUID merchantId) {
        merchantScope.apply(merchantId);
        return scorecards.findByMerchantId(merchantId);
    }
}
