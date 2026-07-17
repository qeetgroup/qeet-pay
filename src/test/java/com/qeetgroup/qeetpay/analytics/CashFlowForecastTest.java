package com.qeetgroup.qeetpay.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
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
 * Cash-flow forecast (PRD Module 12.5): projects the settlement balance forward from the current
 * ledger balance plus the recent net daily inflow, and recommends working capital when the trend is flat.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CashFlowForecastTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired AnalyticsIngestor ingestor;
    @Autowired CashFlowForecastService forecast;
    @Autowired LedgerService ledger;

    @Test
    void projectsFromLedgerBalanceAndRecentInflow() {
        UUID merchantId = newMerchant();
        // Seed a ₹1,000 settlement balance (debit settlement / credit revenue).
        seedSettlement(merchantId, 100_000L);
        // ₹3,000 of captured TPV in the window → avg 10,000 paise/day over 30 days.
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 200_000L, "UPI");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 100_000L, "CARD");

        CashFlowForecastService.CashFlowForecast f = forecast.forecast(merchantId, 30, 30);

        assertThat(f.startingBalanceMinor()).isEqualTo(100_000L);
        assertThat(f.avgDailyNetMinor()).isEqualTo(10_000L);       // 300,000 / 30
        assertThat(f.points()).hasSize(30);
        assertThat(f.projectedEndBalanceMinor()).isEqualTo(100_000L + 30 * 10_000L); // 400,000
        assertThat(f.points().get(29).projectedBalanceMinor()).isEqualTo(400_000L);
        assertThat(f.recommendation()).contains("Healthy");
    }

    @Test
    void refundsReduceProjectedNet() {
        UUID merchantId = newMerchant();
        ingestor.recordPayment(merchantId, UUID.randomUUID(), PaymentAnalyticsEvent.CAPTURED, 300_000L, "UPI");
        ingestor.recordPayment(merchantId, UUID.randomUUID(), "REFUNDED", 300_000L, "UPI");

        CashFlowForecastService.CashFlowForecast f = forecast.forecast(merchantId, 30, 30);
        assertThat(f.avgDailyNetMinor()).isZero();              // captured − refunded == 0
        assertThat(f.recommendation()).contains("working-capital advance");
    }

    @Test
    void noHistoryRecommendsWorkingCapital() {
        UUID merchantId = newMerchant();
        CashFlowForecastService.CashFlowForecast f = forecast.forecast(merchantId, 30, 30);
        assertThat(f.startingBalanceMinor()).isZero();
        assertThat(f.avgDailyNetMinor()).isZero();
        assertThat(f.recommendation()).contains("working-capital advance");
    }

    private UUID newMerchant() {
        return merchants.create("cff-" + UUID.randomUUID().toString().substring(0, 8), "Forecast Co")
                .merchant().getId();
    }

    private void seedSettlement(UUID merchantId, long amountMinor) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        ledger.postEntry(merchantId, "seed", "INR", List.of(
                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                new LedgerLineInput(revenue, Direction.CREDIT, amountMinor)));
    }
}
