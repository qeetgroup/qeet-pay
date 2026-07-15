package com.qeetgroup.qeetpay.analytics;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash-flow forecast (PRD Module 12.5, TAD §5 "Analytics" Phase-2). A deterministic baseline of the
 * "AI cash-flow forecast": projects the settlement balance forward by extrapolating the recent net
 * daily movement (captured − refunded TPV over a trailing window) from the {@code settlement} ledger
 * balance, and surfaces a plain-English working-capital recommendation. The ML forecast (§8) layers
 * on top of this same shape later. Reads only the {@code ledger} balance and analytics tables —
 * within the analytics module's allowed dependencies.
 */
@Service
public class CashFlowForecastService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final JdbcClient jdbc;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;

    public CashFlowForecastService(JdbcClient jdbc, LedgerService ledger, MerchantScope merchantScope) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
    }

    @Transactional(readOnly = true)
    public CashFlowForecast forecast(UUID merchantId, int horizonDays, int windowDays) {
        merchantScope.apply(merchantId);
        if (horizonDays < 1 || horizonDays > 365) {
            throw new IllegalArgumentException("horizonDays must be between 1 and 365");
        }
        if (windowDays < 1 || windowDays > 365) {
            throw new IllegalArgumentException("windowDays must be between 1 and 365");
        }

        long startingBalance = ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "settlement").getId());

        Instant since = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        long netInflow =
                jdbc.sql(
                                """
                                SELECT COALESCE(SUM(CASE WHEN event_type = 'CAPTURED' THEN amount_minor ELSE 0 END), 0)
                                     - COALESCE(SUM(CASE WHEN event_type = 'REFUNDED' THEN amount_minor ELSE 0 END), 0)
                                FROM analytics.payment_events
                                WHERE merchant_id = :mid AND occurred_at >= :since
                                """)
                        .param("mid", merchantId)
                        .param("since", Timestamp.from(since))
                        .query(Long.class)
                        .single();

        long avgDailyNet = netInflow / windowDays;

        List<ForecastPoint> points = new ArrayList<>(horizonDays);
        LocalDate today = LocalDate.now(INDIA);
        for (int d = 1; d <= horizonDays; d++) {
            points.add(new ForecastPoint(d, today.plusDays(d), startingBalance + (long) d * avgDailyNet));
        }
        long projectedEnd = startingBalance + (long) horizonDays * avgDailyNet;

        return new CashFlowForecast(
                startingBalance, avgDailyNet, horizonDays, projectedEnd, recommend(avgDailyNet, projectedEnd), points);
    }

    private String recommend(long avgDailyNet, long projectedEnd) {
        if (avgDailyNet <= 0) {
            return "Recent net inflow is flat or negative — consider a working-capital advance to bridge cash flow.";
        }
        if (projectedEnd < 0) {
            return "Projected balance turns negative within the horizon — consider a working-capital advance.";
        }
        return "Healthy positive cash-flow trajectory over the horizon.";
    }

    /** One day of the projection. */
    public record ForecastPoint(int day, LocalDate date, long projectedBalanceMinor) {}

    /** A settlement-balance projection plus a working-capital recommendation. */
    public record CashFlowForecast(
            long startingBalanceMinor,
            long avgDailyNetMinor,
            int horizonDays,
            long projectedEndBalanceMinor,
            String recommendation,
            List<ForecastPoint> points) {}
}
