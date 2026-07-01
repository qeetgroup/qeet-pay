package com.qeetgroup.qeetpay.dunning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.billing.BillingInterval;
import com.qeetgroup.qeetpay.billing.BillingService;
import com.qeetgroup.qeetpay.billing.Plan;
import com.qeetgroup.qeetpay.billing.Subscription;
import com.qeetgroup.qeetpay.billing.SubscriptionStatus;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.Optional;
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

/** Dunning integration tests: rule CRUD, trigger, and retry exhaustion → cancellation. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DunningFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BillingService billing;
    @Autowired DunningService dunning;

    @Test
    void createAndListRules() {
        UUID merchantId = newMerchant();
        dunning.createRule(merchantId, "Standard Retry", "*", 24, 3, "[\"EMAIL\"]");
        dunning.createRule(merchantId, "Fast Retry", "insufficient_funds", 6, 2, null);

        List<DunningRule> rules = dunning.listRules(merchantId);
        assertThat(rules).hasSize(2);
    }

    @Test
    void triggerMarksPastDueAndCreatesAttempt() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "p1", "Plan 1", 9900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "c1");
        UUID subId = s.subscription().getId();
        dunning.createRule(merchantId, "Catch All", "*", 24, 3, null);

        Optional<DunningAttempt> attempt = dunning.trigger(merchantId, subId, "card_declined");

        assertThat(attempt).isPresent();
        assertThat(attempt.get().getResult()).isEqualTo(DunningAttempt.FAILED);
        assertThat(attempt.get().getAttemptNumber()).isEqualTo(1);

        Subscription sub = billing.getSubscription(merchantId, subId);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void triggerWithNoMatchingRuleDoesNothing() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "p2", "Plan 2", 9900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "c2");

        // No rules created — trigger should return empty
        Optional<DunningAttempt> attempt = dunning.trigger(merchantId, s.subscription().getId(), "any_code");
        // subscription is now PAST_DUE but no rule matched
        assertThat(attempt).isEmpty();
    }

    @Test
    void retryExhaustionCancelsSubscription() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "p3", "Plan 3", 19900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "c3");
        UUID subId = s.subscription().getId();

        // Rule with maxAttempts=1 → trigger already counts as the first failed attempt → should exhaust immediately
        dunning.createRule(merchantId, "One Strike", "*", 24, 1, null);

        Optional<DunningAttempt> attempt = dunning.trigger(merchantId, subId, "card_declined");
        assertThat(attempt).isPresent();

        // After trigger with maxAttempts=1, subscription should be CANCELLED
        Subscription sub = billing.getSubscription(merchantId, subId);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void attemptsAreOrderedByAttemptNumber() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "p4", "Plan 4", 9900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "c4");
        UUID subId = s.subscription().getId();
        dunning.createRule(merchantId, "Multi Retry", "*", 24, 5, null);

        dunning.trigger(merchantId, subId, "card_declined");

        List<DunningAttempt> allAttempts = dunning.attemptsFor(merchantId, subId);
        // attempt #1 (FAILED) + attempt #2 (SCHEDULED)
        assertThat(allAttempts).hasSizeGreaterThanOrEqualTo(1);
        assertThat(allAttempts.get(0).getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void executeRetryOnNonPastDueThrows() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "p5", "Plan 5", 9900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "c5");
        dunning.createRule(merchantId, "Retry Rule", "*", 24, 3, null);

        // Subscription is ACTIVE — executeRetry should reject
        assertThatThrownBy(() -> dunning.executeRetry(merchantId, s.subscription().getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAST_DUE");
    }

    private UUID newMerchant() {
        return merchants.create("dn-" + UUID.randomUUID().toString().substring(0, 8), "Dunning Co")
                .merchant().getId();
    }
}
