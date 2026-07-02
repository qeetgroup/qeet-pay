package com.qeetgroup.qeetpay.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Analytics engine integration tests — TPV, MRR waterfall, ARR, success rate. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AnalyticsQueryServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired AnalyticsIngestor ingestor;
    @Autowired AnalyticsQueryService analytics;

    @Test
    void tpvAggregatesCapturedByDay() {
        UUID merchantId = newMerchant();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        ingestor.recordPayment(merchantId, p1, PaymentAnalyticsEvent.CAPTURED, 50000L, "UPI");
        ingestor.recordPayment(merchantId, p2, PaymentAnalyticsEvent.CAPTURED, 30000L, "CARD");
        ingestor.recordPayment(merchantId, p3, PaymentAnalyticsEvent.FAILED,   20000L, "UPI");

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);
        List<AnalyticsQueryService.TpvBucket> buckets = analytics.tpvByPeriod(merchantId, from, to, "DAY");

        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).totalMinor()).isEqualTo(80000L); // 50k + 30k only (FAILED excluded)
        assertThat(buckets.get(0).txCount()).isEqualTo(2);
    }

    @Test
    void tpvEmptyWhenNoEvents() {
        UUID merchantId = newMerchant();
        Instant from = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant to   = Instant.now().minus(1, ChronoUnit.HOURS);

        List<AnalyticsQueryService.TpvBucket> buckets = analytics.tpvByPeriod(merchantId, from, to, "DAY");
        assertThat(buckets).isEmpty();
    }

    @Test
    void successRateCalculatedCorrectly() {
        UUID merchantId = newMerchant();
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 10000L, "UPI");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 10000L, "UPI");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.FAILED,   10000L, "UPI");

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);
        AnalyticsQueryService.SuccessRate rate = analytics.successRate(merchantId, from, to, null);

        assertThat(rate.captured()).isEqualTo(2);
        assertThat(rate.failed()).isEqualTo(1);
        assertThat(rate.ratePercent()).isCloseTo(66.67, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void currentMrrSumsSignedDeltas() {
        UUID merchantId = newMerchant();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        ingestor.recordSubscriptionEvent(merchantId, s1, SubscriptionAnalyticsEvent.NEW, 100000L);
        ingestor.recordSubscriptionEvent(merchantId, s2, SubscriptionAnalyticsEvent.NEW, 50000L);
        ingestor.recordSubscriptionEvent(merchantId, s1, SubscriptionAnalyticsEvent.CHURN, -100000L);

        long mrr = analytics.currentMrrMinor(merchantId);
        assertThat(mrr).isEqualTo(50000L);
        assertThat(analytics.currentArrMinor(merchantId)).isEqualTo(600000L);
    }

    @Test
    void mrrWaterfallGroupsByMonth() {
        UUID merchantId = newMerchant();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        ingestor.recordSubscriptionEvent(merchantId, s1, SubscriptionAnalyticsEvent.NEW,       200000L);
        ingestor.recordSubscriptionEvent(merchantId, s2, SubscriptionAnalyticsEvent.EXPANSION,  50000L);
        ingestor.recordSubscriptionEvent(merchantId, s1, SubscriptionAnalyticsEvent.CHURN,    -200000L);

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);
        List<AnalyticsQueryService.MrrWaterfallRow> rows = analytics.mrrWaterfall(merchantId, from, to);

        assertThat(rows).hasSize(1);
        AnalyticsQueryService.MrrWaterfallRow row = rows.get(0);
        assertThat(row.newMrr()).isEqualTo(200000L);
        assertThat(row.expansion()).isEqualTo(50000L);
        assertThat(row.churn()).isEqualTo(-200000L);
        assertThat(row.netChange()).isEqualTo(50000L);
    }

    @Test
    void merchantIsolationHolds() {
        UUID m1 = newMerchant();
        UUID m2 = newMerchant();

        ingestor.recordPayment(m1, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 99999L, "CARD");

        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to   = Instant.now().plus(1, ChronoUnit.HOURS);
        List<AnalyticsQueryService.TpvBucket> m2Buckets = analytics.tpvByPeriod(m2, from, to, "DAY");
        assertThat(m2Buckets).isEmpty();
    }

    private UUID newMerchant() {
        return merchants.create("an-" + UUID.randomUUID().toString().substring(0, 8), "Analytics Co")
                .merchant().getId();
    }
}
