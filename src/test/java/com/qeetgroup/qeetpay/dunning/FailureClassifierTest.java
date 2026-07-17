package com.qeetgroup.qeetpay.dunning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure UPI-failure classification: each category maps to its adaptive, explainable recommendation. */
class FailureClassifierTest {

    private final FailureClassifier classifier = new FailureClassifier();

    @Test
    void insufficientFundsRetriesLaterWithReminders() {
        RetryRecommendation r = classifier.classify("INSUFFICIENT_FUNDS");
        assertThat(r.category()).isEqualTo(FailureCategory.INSUFFICIENT_FUNDS);
        assertThat(r.retryable()).isTrue();
        assertThat(r.recommendedDelayHours()).isEqualTo(48);
        assertThat(r.recommendedChannels()).contains("WHATSAPP");
        assertThat(r.rationale()).isNotBlank();
    }

    @Test
    void limitExceededRetriesNextDay() {
        RetryRecommendation r = classifier.classify("TXN_LIMIT_EXCEEDED");
        assertThat(r.category()).isEqualTo(FailureCategory.LIMIT_EXCEEDED);
        assertThat(r.recommendedDelayHours()).isEqualTo(24);
    }

    @Test
    void technicalDeclineRetriesSoonAndSilently() {
        RetryRecommendation r = classifier.classify("GATEWAY_TIMEOUT");
        assertThat(r.category()).isEqualTo(FailureCategory.TECHNICAL_DECLINE);
        assertThat(r.recommendedDelayHours()).isEqualTo(1);
        assertThat(r.recommendedChannels()).isEmpty(); // silent retry, no customer nudge
    }

    @Test
    void riskDeclineIsNotRetryable() {
        RetryRecommendation r = classifier.classify("RISK_BLOCKED");
        assertThat(r.category()).isEqualTo(FailureCategory.RISK_DECLINE);
        assertThat(r.retryable()).isFalse();
        assertThat(r.recommendedDelayHours()).isZero();
    }

    @Test
    void mandateIssueNeedsReauthorisation() {
        RetryRecommendation r = classifier.classify("MANDATE_REVOKED");
        assertThat(r.category()).isEqualTo(FailureCategory.MANDATE_ISSUE);
        assertThat(r.retryable()).isFalse();
    }

    @Test
    void customerActionNudgesThenRetries() {
        RetryRecommendation r = classifier.classify("CARD_EXPIRED");
        assertThat(r.category()).isEqualTo(FailureCategory.CUSTOMER_ACTION);
        assertThat(r.recommendedDelayHours()).isEqualTo(12);
    }

    @Test
    void unknownFallsBackToDefault() {
        RetryRecommendation r = classifier.classify("SOME_WEIRD_CODE_9Z");
        assertThat(r.category()).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(r.recommendedDelayHours()).isEqualTo(24);
        assertThat(classifier.categorize(null)).isEqualTo(FailureCategory.UNKNOWN);
        assertThat(classifier.categorize("   ")).isEqualTo(FailureCategory.UNKNOWN);
    }
}
