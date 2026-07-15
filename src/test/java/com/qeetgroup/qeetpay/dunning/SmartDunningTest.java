package com.qeetgroup.qeetpay.dunning;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.billing.BillingInterval;
import com.qeetgroup.qeetpay.billing.BillingService;
import com.qeetgroup.qeetpay.billing.Plan;
import com.qeetgroup.qeetpay.billing.Subscription;
import com.qeetgroup.qeetpay.billing.SubscriptionStatus;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * AI-dunning flow (PRD Module 04.1): triggerSmart classifies the failure, records the classification
 * on the attempt, and adapts the next step — a retryable category schedules an adaptive retry, a
 * non-retryable one (risk/mandate) stops auto-recovery and cancels the subscription.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SmartDunningTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BillingService billing;
    @Autowired DunningService dunning;

    @Test
    void retryableFailureSchedulesAdaptiveRetryWithClassification() {
        UUID merchantId = newMerchant();
        UUID subId = newSubscription(merchantId, "sc1");

        DunningAttempt attempt = dunning.triggerSmart(merchantId, subId, "INSUFFICIENT_FUNDS");

        assertThat(attempt.getFailureCategory()).isEqualTo(FailureCategory.INSUFFICIENT_FUNDS.name());
        assertThat(attempt.getRecommendedDelayHours()).isEqualTo(48);
        assertThat(attempt.getRecommendedChannels()).contains("WHATSAPP");
        assertThat(attempt.getClassificationRationale()).isNotBlank();

        assertThat(billing.getSubscription(merchantId, subId).getStatus())
                .isEqualTo(SubscriptionStatus.PAST_DUE);

        // A follow-up adaptive retry was scheduled (attempt #2).
        List<DunningAttempt> all = dunning.attemptsFor(merchantId, subId);
        assertThat(all).hasSize(2);
        assertThat(all.get(1).getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void nonRetryableRiskDeclineCancelsWithoutScheduling() {
        UUID merchantId = newMerchant();
        UUID subId = newSubscription(merchantId, "sc2");

        DunningAttempt attempt = dunning.triggerSmart(merchantId, subId, "RISK_BLOCKED");

        assertThat(attempt.getFailureCategory()).isEqualTo(FailureCategory.RISK_DECLINE.name());
        assertThat(billing.getSubscription(merchantId, subId).getStatus())
                .isEqualTo(SubscriptionStatus.CANCELLED);
        // Only the failed attempt exists — no adaptive retry was scheduled.
        assertThat(dunning.attemptsFor(merchantId, subId)).hasSize(1);
    }

    private UUID newMerchant() {
        return merchants.create("sd-" + UUID.randomUUID().toString().substring(0, 8), "Smart Dunning Co")
                .merchant().getId();
    }

    private UUID newSubscription(UUID merchantId, String customerRef) {
        Plan plan = billing.createPlan(merchantId, "plan-" + customerRef, "Plan", 9900L, "INR", BillingInterval.MONTH);
        return billing.createSubscription(merchantId, plan.getId(), customerRef).subscription().getId();
    }
}
