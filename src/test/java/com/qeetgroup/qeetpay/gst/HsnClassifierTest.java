package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.ai.AiDecision;
import com.qeetgroup.qeetpay.ai.AiDecisionRepository;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * HSN/SAC classification (PRD Module 05). Verifies that the classifier returns ranked suggestions with a
 * confidence + explanation via the AiGateway, that a model error fails closed to the deterministic
 * {@link HsnCatalog} fallback, that a no-keyword description is flagged for human review, and that
 * repeated queries hit the cache.
 */
class HsnClassifierTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired HsnClassifier classifier;
    @Autowired HsnClassificationRepository cache;
    @Autowired AiDecisionRepository decisions;

    @Test
    void returnsRankedSuggestionWithConfidenceAndExplanation() {
        UUID merchantId = newMerchant();

        ClassificationResult result =
                classifier.classify(merchantId, "premium software subscription license", Set.of("gst:classify"));

        assertThat(result.suggestions()).isNotEmpty();
        HsnSuggestion primary = result.suggestions().get(0);
        assertThat(primary.hsnSac()).isEqualTo("998314");
        assertThat(primary.kind()).isEqualTo("SAC");
        assertThat(primary.gstRate()).isEqualTo(18);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(HsnClassifier.REVIEW_THRESHOLD);
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.explanation()).isNotBlank();
        assertThat(result.decisionId()).isNotBlank();
        // The offline sandbox stub carries no HSN, so the deterministic candidates are used.
        assertThat(result.source()).isEqualTo("deterministic");
        assertThat(result.fellBack()).isFalse();
    }

    @Test
    void fallsBackDeterministicallyOnModelError() {
        UUID merchantId = newMerchant();

        // "fail_" makes the sandbox model client throw (simulated timeout/error) → gateway fails closed.
        ClassificationResult result =
                classifier.classify(merchantId, "management consulting advisory fail_now", Set.of());

        assertThat(result.fellBack()).isTrue();
        assertThat(result.source()).isEqualTo("deterministic");
        assertThat(result.suggestions()).isNotEmpty();
        assertThat(result.suggestions().get(0).hsnSac()).isEqualTo("998311");
        assertThat(result.confidence()).isGreaterThan(0.0);

        // The gateway recorded an append-only decision row that fell back.
        List<AiDecision> rows = decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        assertThat(rows).isNotEmpty();
        assertThat(rows.get(0).isFellBack()).isTrue();
    }

    @Test
    void lowConfidenceNoKeywordMatchFlagsHumanReview() {
        UUID merchantId = newMerchant();

        ClassificationResult result = classifier.classify(merchantId, "zzz qqq wubble flooble", Set.of());

        assertThat(result.requiresReview()).isTrue();
        assertThat(result.confidence()).isLessThan(HsnClassifier.REVIEW_THRESHOLD);
        assertThat(result.suggestions().get(0).hsnSac()).isEqualTo(HsnCatalog.RESIDUAL.hsnSac());
    }

    @Test
    void cachesRepeatedClassification() {
        UUID merchantId = newMerchant();

        ClassificationResult first = classifier.classify(merchantId, "leather footwear shoe", Set.of());
        ClassificationResult second = classifier.classify(merchantId, "leather footwear shoe", Set.of());

        assertThat(second.suggestions().get(0).hsnSac()).isEqualTo(first.suggestions().get(0).hsnSac());

        List<HsnClassification> rows =
                cache.findAll().stream().filter(c -> c.getMerchantId().equals(merchantId)).toList();
        assertThat(rows).hasSize(1); // one cache row for the (merchant, query)
        assertThat(rows.get(0).getHitCount()).isGreaterThanOrEqualTo(1); // the second call was a hit
    }

    private UUID newMerchant() {
        return merchants.create("hsn-" + UUID.randomUUID().toString().substring(0, 8), "HSN Co")
                .merchant()
                .getId();
    }
}
