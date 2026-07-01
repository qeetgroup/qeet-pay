package com.qeetgroup.qeetpay.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.Instant;
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

/** Usage ingestion and period aggregation for metered plans. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class UsageMeteringTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BillingService billing;
    @Autowired UsageMeterService usageMeter;

    @Test
    void ingestAndAggregateWithinPeriod() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "metered-1", "Metered Plan",
                100L, "INR", BillingInterval.MONTH, PricingModel.PER_UNIT, null, "api_calls", 0);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "metered-cust-1");
        UUID subId = s.subscription().getId();

        Instant before = Instant.now().minusSeconds(1);
        usageMeter.ingest(merchantId, subId, "api_calls", 100L, null);
        usageMeter.ingest(merchantId, subId, "api_calls", 250L, null);
        Instant after = Instant.now().plusSeconds(1);

        long total = usageMeter.aggregateQuantity(merchantId, subId, "api_calls", before, after);
        assertThat(total).isEqualTo(350L);
    }

    @Test
    void idempotentIngest() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "metered-2", "Metered Plan 2",
                100L, "INR", BillingInterval.MONTH, PricingModel.PER_UNIT, null, "sms", 0);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "metered-cust-2");
        UUID subId = s.subscription().getId();

        Instant before = Instant.now().minusSeconds(1);
        usageMeter.ingest(merchantId, subId, "sms", 50L, "key-1");
        usageMeter.ingest(merchantId, subId, "sms", 50L, "key-1"); // duplicate
        Instant after = Instant.now().plusSeconds(1);

        long total = usageMeter.aggregateQuantity(merchantId, subId, "sms", before, after);
        assertThat(total).isEqualTo(50L); // only counted once
    }

    @Test
    void aggregationExcludesOutOfWindowEvents() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "metered-3", "Metered Plan 3",
                100L, "INR", BillingInterval.MONTH, PricingModel.PER_UNIT, null, "emails", 0);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "metered-cust-3");
        UUID subId = s.subscription().getId();

        usageMeter.ingest(merchantId, subId, "emails", 200L, null);

        // Query a window that is entirely in the future
        Instant futureFrom = Instant.now().plusSeconds(3600);
        Instant futureTo = futureFrom.plusSeconds(7200);
        long total = usageMeter.aggregateQuantity(merchantId, subId, "emails", futureFrom, futureTo);
        assertThat(total).isEqualTo(0L);
    }

    @Test
    void cannotIngestOnPausedSubscription() {
        UUID merchantId = newMerchant();
        Plan plan = billing.createPlan(merchantId, "metered-4", "Metered 4",
                100L, "INR", BillingInterval.MONTH, PricingModel.PER_UNIT, null, "txns", 0);
        BillingService.Subscribed s = billing.createSubscription(merchantId, plan.getId(), "metered-cust-4");
        UUID subId = s.subscription().getId();
        billing.pauseSubscription(merchantId, subId);

        assertThatThrownBy(() -> usageMeter.ingest(merchantId, subId, "txns", 10L, null))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("um-" + UUID.randomUUID().toString().substring(0, 8), "Usage Co")
                .merchant().getId();
    }
}
