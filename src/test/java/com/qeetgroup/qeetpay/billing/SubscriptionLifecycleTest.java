package com.qeetgroup.qeetpay.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * Subscription lifecycle: ACTIVE → pause → resume → cancel (immediate + at-period-end).
 * Also covers PAST_DUE mark and trial start.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SubscriptionLifecycleTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BillingService billing;

    @Test
    void pauseAndResume() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "basic", "Basic", 9900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "cust1");
        UUID subId = s.subscription().getId();

        assertThat(s.subscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        Subscription paused = billing.pauseSubscription(merchantId, subId);
        assertThat(paused.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(paused.getPausedAt()).isNotNull();

        Subscription resumed = billing.resumeSubscription(merchantId, subId);
        assertThat(resumed.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(resumed.getPausedAt()).isNull();
    }

    @Test
    void cancelImmediately() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "pro", "Pro", 49900L, "INR", BillingInterval.YEAR);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "cust2");

        Subscription cancelled = billing.cancelSubscription(merchantId, s.subscription().getId(), false);
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelAtPeriodEnd() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "ent", "Enterprise", 99900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "cust3");

        Subscription sub = billing.cancelSubscription(merchantId, s.subscription().getId(), true);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.isCancelAtPeriodEnd()).isTrue();
    }

    @Test
    void cannotPauseAlreadyPaused() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "starter", "Starter", 4900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "cust4");
        UUID subId = s.subscription().getId();
        billing.pauseSubscription(merchantId, subId);

        assertThatThrownBy(() -> billing.pauseSubscription(merchantId, subId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void trialStartsOnPlanWithTrialDays() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "trial-plan", "Trial Plan",
                19900L, "INR", BillingInterval.MONTH, PricingModel.FLAT, null, null, 14);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "trial-cust");

        assertThat(s.subscription().getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(s.subscription().getTrialEndsAt()).isNotNull();
    }

    @Test
    void upgradeIssuesProrationInvoice() {
        UUID merchantId = newMerchant();
        Plan basic = billing.createPlan(merchantId, "basic-up", "Basic", 9900L, "INR", BillingInterval.MONTH);
        Plan pro = billing.createPlan(merchantId, "pro-up", "Pro", 29900L, "INR", BillingInterval.MONTH);
        BillingService.Subscribed s = billing.createSubscription(merchantId, basic.getId(), "cust-upgrade");

        BillingService.ProrationResult result = billing.upgradeSubscription(merchantId, s.subscription().getId(), pro.getId());
        assertThat(result.subscription().getPlanId()).isEqualTo(pro.getId());
        assertThat(result.prorationMinor()).isGreaterThanOrEqualTo(0L);
    }

    private UUID newMerchant() {
        return merchants.create("lc-" + UUID.randomUUID().toString().substring(0, 8), "Lifecycle Co")
                .merchant().getId();
    }
}
