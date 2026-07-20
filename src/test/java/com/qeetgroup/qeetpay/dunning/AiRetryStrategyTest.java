package com.qeetgroup.qeetpay.dunning;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ai.AiDecision;
import com.qeetgroup.qeetpay.ai.AiDecisionRepository;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI dunning strategy (PRD Module 04.2 "Smart Retry" + 04.5 "Explainable Dunning"). Verifies the
 * recommendation is produced through the AI gateway (masked + audited), personalises timing/channels/
 * tone from engagement signals on the model path, and <b>falls back to the deterministic
 * {@link FailureClassifier} heuristic</b> when the model client errors — the money-affecting retry/stop
 * decision staying deterministic throughout.
 */
class AiRetryStrategyTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired AiRetryStrategy strategy;
    @Autowired DunningService dunning;
    @Autowired AiDecisionRepository decisions;

    @Test
    void aiPathPersonalisesTimingChannelsAndToneAndAuditsWithMasking() {
        UUID merchantId = newMerchant();

        // Payer's payday is 3 days out; prefers SMS; a phone in the contact hint must be masked.
        AiRetryPlan plan =
                strategy.recommend(
                        merchantId,
                        "INSUFFICIENT_FUNDS",
                        new EngagementSignals(3, 0.9, "SMS", "hi", "reach me at 9876543210"),
                        Set.of("dunning:read"));

        assertThat(plan.aiAssisted()).isTrue();
        assertThat(plan.category()).isEqualTo(FailureCategory.INSUFFICIENT_FUNDS);
        assertThat(plan.retryable()).isTrue(); // deterministic classifier's call
        assertThat(plan.recommendedDelayHours()).isEqualTo(3 * 24 + 6); // payday-aware = 78h
        assertThat(plan.channelOrder().get(0)).isEqualTo("SMS"); // preferred channel fronted
        assertThat(plan.messageTone()).isEqualTo("empathetic");
        assertThat(plan.reasons()).isNotEmpty();
        assertThat(plan.decisionId()).isNotNull();

        // The AI gateway audited the decision and masked the phone before persisting.
        AiDecision row = decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId).get(0);
        assertThat(row.getFeature()).isEqualTo(AiRetryStrategy.FEATURE);
        assertThat(row.isFellBack()).isFalse();
        assertThat(row.getMaskedInput()).doesNotContain("9876543210").contains("[PHONE]");
    }

    @Test
    void fallsBackToDeterministicHeuristicWhenModelErrors() {
        UUID merchantId = newMerchant();

        // "fail_" in the input makes the offline model client throw — the gateway fails closed.
        AiRetryPlan plan =
                strategy.recommend(
                        merchantId, "technical fail_now", EngagementSignals.unknown(), Set.of());

        assertThat(plan.aiAssisted()).isFalse();
        assertThat(plan.category()).isEqualTo(FailureCategory.TECHNICAL_DECLINE);
        assertThat(plan.retryable()).isTrue(); // still the deterministic classifier's call
        assertThat(plan.channelOrder()).isEmpty(); // transient technical → deliberately silent
        assertThat(plan.reasons().get(plan.reasons().size() - 1)).contains("deterministic");
        assertThat(plan.decisionId()).isNotNull();

        AiDecision row = decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId).get(0);
        assertThat(row.isFellBack()).isTrue();
    }

    @Test
    void serviceEntrypointRecommendsAndKeepsMoneyDecisionDeterministic() {
        UUID merchantId = newMerchant();

        AiRetryPlan plan =
                dunning.recommendRetry(
                        merchantId, "MANDATE_REVOKED", EngagementSignals.unknown(), Set.of());

        assertThat(plan.category()).isEqualTo(FailureCategory.MANDATE_ISSUE);
        assertThat(plan.retryable()).isFalse(); // mandate issue → no auto-retry (deterministic)
        assertThat(plan.reasons()).isNotEmpty();
    }

    private UUID newMerchant() {
        return merchants.create("air-" + UUID.randomUUID().toString().substring(0, 8), "AI Retry Co")
                .merchant().getId();
    }
}
