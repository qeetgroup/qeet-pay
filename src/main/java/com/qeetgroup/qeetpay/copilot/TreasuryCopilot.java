package com.qeetgroup.qeetpay.copilot;

import com.qeetgroup.qeetpay.ai.AiFeature;
import com.qeetgroup.qeetpay.analytics.AnalyticsQueryService;
import com.qeetgroup.qeetpay.analytics.CashFlowForecastService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Treasury &amp; Cash-Flow Copilot (PRD Module 12.5). Answers cash-flow / settlement / working-capital
 * questions by pulling the merchant's own figures from the {@code analytics} public read APIs — the
 * settlement-balance cash-flow forecast plus trailing TPV, MRR/ARR and success rate — and routing the
 * answer through the {@link com.qeetgroup.qeetpay.ai.AiGateway} (via {@link CopilotService}). The
 * deterministic summary composed here is both the audited fallback and the answer surfaced under the
 * offline sandbox model stand-in. Every answer cites the figures it used.
 */
@Service
public class TreasuryCopilot {

    static final String FEATURE = AiFeature.TREASURY_COPILOT.key();
    private static final int HORIZON_DAYS = 30;
    private static final int WINDOW_DAYS = 30;

    private final CashFlowForecastService cashFlow;
    private final AnalyticsQueryService analytics;
    private final CopilotService copilot;

    public TreasuryCopilot(
            CashFlowForecastService cashFlow, AnalyticsQueryService analytics, CopilotService copilot) {
        this.cashFlow = cashFlow;
        this.analytics = analytics;
        this.copilot = copilot;
    }

    @Transactional
    public CopilotAnswer ask(UUID merchantId, UUID conversationId, String question) {
        CashFlowForecastService.CashFlowForecast forecast =
                cashFlow.forecast(merchantId, HORIZON_DAYS, WINDOW_DAYS);

        Instant now = Instant.now();
        Instant from = now.minus(WINDOW_DAYS, ChronoUnit.DAYS);
        long trailingTpv =
                analytics.tpvByPeriod(merchantId, from, now, "DAY").stream()
                        .mapToLong(AnalyticsQueryService.TpvBucket::totalMinor)
                        .sum();
        long mrr = analytics.currentMrrMinor(merchantId);
        long arr = analytics.currentArrMinor(merchantId);
        AnalyticsQueryService.SuccessRate rate = analytics.successRate(merchantId, from, now, null);

        List<Figure> figures =
                List.of(
                        Figure.money("settlement_balance", "Settlement balance", forecast.startingBalanceMinor()),
                        Figure.money(
                                "avg_daily_net", "Avg daily net movement (30d)", forecast.avgDailyNetMinor()),
                        Figure.money(
                                "projected_balance_30d",
                                "Projected settlement balance (30d)",
                                forecast.projectedEndBalanceMinor()),
                        Figure.money("trailing_tpv_30d", "Trailing TPV (30d)", trailingTpv),
                        Figure.money("mrr", "MRR", mrr),
                        Figure.money("arr", "ARR", arr),
                        Figure.percent("success_rate_30d", "Payment success rate (30d)", rate.ratePercent()));

        List<String> citations =
                List.of(
                        "analytics.cash-flow-forecast (horizon 30d, window 30d)",
                        "analytics.tpv (trailing 30d)",
                        "analytics.mrr",
                        "analytics.arr",
                        "analytics.success-rate (30d)");

        String answer = summarise(question, forecast, trailingTpv, mrr, arr, rate);
        return copilot.respond(
                merchantId, conversationId, CopilotSurface.TREASURY, question, FEATURE, figures, citations, answer);
    }

    private String summarise(
            String question,
            CashFlowForecastService.CashFlowForecast forecast,
            long trailingTpv,
            long mrr,
            long arr,
            AnalyticsQueryService.SuccessRate rate) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("Your settlement balance is ")
                .append(CopilotFormat.inr(forecast.startingBalanceMinor()))
                .append(". Over the trailing 30 days the net daily movement is ")
                .append(CopilotFormat.inr(forecast.avgDailyNetMinor()))
                .append("/day, so the projected settlement balance in 30 days is ")
                .append(CopilotFormat.inr(forecast.projectedEndBalanceMinor()))
                .append(". ")
                .append(forecast.recommendation());

        if (q.contains("mrr") || q.contains("recurring") || q.contains("subscription") || q.contains("arr")) {
            sb.append(" Recurring revenue: MRR ")
                    .append(CopilotFormat.inr(mrr))
                    .append(" (ARR ")
                    .append(CopilotFormat.inr(arr))
                    .append(").");
        } else {
            sb.append(" Trailing 30-day TPV is ")
                    .append(CopilotFormat.inr(trailingTpv))
                    .append(" at a ")
                    .append(CopilotFormat.pct(rate.ratePercent()))
                    .append(" success rate; MRR is ")
                    .append(CopilotFormat.inr(mrr))
                    .append(".");
        }
        return sb.toString();
    }
}
