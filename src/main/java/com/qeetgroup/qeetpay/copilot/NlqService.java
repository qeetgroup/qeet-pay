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
 * Natural-Language Query (PRD Module 17). Turns a plain-English question into a structured answer over
 * the merchant's own metrics. A <b>deterministic intent-matcher</b> maps the question to one metric
 * (TPV, MRR, ARR, success rate, or cash-flow) from the {@code analytics} public read APIs — this is the
 * fallback the {@link com.qeetgroup.qeetpay.ai.AiGateway} uses when the model stand-in is in play (PRD
 * Module 17). The answer (and the figures it cites) always route through and are audited by the gateway.
 */
@Service
public class NlqService {

    static final String FEATURE = AiFeature.NLQ.key();
    private static final int WINDOW_DAYS = 30;

    private final AnalyticsQueryService analytics;
    private final CashFlowForecastService cashFlow;
    private final CopilotService copilot;

    public NlqService(
            AnalyticsQueryService analytics, CashFlowForecastService cashFlow, CopilotService copilot) {
        this.analytics = analytics;
        this.cashFlow = cashFlow;
        this.copilot = copilot;
    }

    private enum Intent {
        TPV,
        MRR,
        ARR,
        SUCCESS_RATE,
        CASHFLOW,
        SUMMARY
    }

    @Transactional
    public CopilotAnswer ask(UUID merchantId, UUID conversationId, String question) {
        Intent intent = detect(question);
        Instant now = Instant.now();
        Instant from = now.minus(WINDOW_DAYS, ChronoUnit.DAYS);

        List<Figure> figures;
        List<String> citations;
        String answer;

        switch (intent) {
            case TPV -> {
                long tpv =
                        analytics.tpvByPeriod(merchantId, from, now, "DAY").stream()
                                .mapToLong(AnalyticsQueryService.TpvBucket::totalMinor)
                                .sum();
                figures = List.of(Figure.money("trailing_tpv_30d", "Trailing TPV (30d)", tpv));
                citations = List.of("analytics.tpv (trailing 30d)");
                answer = "Your total payment volume over the last 30 days is " + CopilotFormat.inr(tpv) + ".";
            }
            case MRR -> {
                long mrr = analytics.currentMrrMinor(merchantId);
                figures = List.of(Figure.money("mrr", "MRR", mrr));
                citations = List.of("analytics.mrr");
                answer = "Your current monthly recurring revenue (MRR) is " + CopilotFormat.inr(mrr) + ".";
            }
            case ARR -> {
                long arr = analytics.currentArrMinor(merchantId);
                figures = List.of(Figure.money("arr", "ARR", arr));
                citations = List.of("analytics.arr");
                answer = "Your annual recurring revenue (ARR) is " + CopilotFormat.inr(arr) + ".";
            }
            case SUCCESS_RATE -> {
                AnalyticsQueryService.SuccessRate rate = analytics.successRate(merchantId, from, now, null);
                figures =
                        List.of(
                                Figure.percent("success_rate_30d", "Payment success rate (30d)", rate.ratePercent()),
                                Figure.count("captured_30d", "Captured payments (30d)", rate.captured()),
                                Figure.count("failed_30d", "Failed payments (30d)", rate.failed()));
                citations = List.of("analytics.success-rate (30d)");
                answer =
                        "Your payment success rate over the last 30 days is "
                                + CopilotFormat.pct(rate.ratePercent())
                                + " ("
                                + rate.captured()
                                + " captured, "
                                + rate.failed()
                                + " failed).";
            }
            case CASHFLOW -> {
                CashFlowForecastService.CashFlowForecast forecast = cashFlow.forecast(merchantId, 30, WINDOW_DAYS);
                figures =
                        List.of(
                                Figure.money("settlement_balance", "Settlement balance", forecast.startingBalanceMinor()),
                                Figure.money(
                                        "projected_balance_30d",
                                        "Projected settlement balance (30d)",
                                        forecast.projectedEndBalanceMinor()));
                citations = List.of("analytics.cash-flow-forecast (horizon 30d, window 30d)");
                answer =
                        "Your settlement balance is "
                                + CopilotFormat.inr(forecast.startingBalanceMinor())
                                + ", projected to be "
                                + CopilotFormat.inr(forecast.projectedEndBalanceMinor())
                                + " in 30 days. "
                                + forecast.recommendation();
            }
            default -> {
                long tpv =
                        analytics.tpvByPeriod(merchantId, from, now, "DAY").stream()
                                .mapToLong(AnalyticsQueryService.TpvBucket::totalMinor)
                                .sum();
                long mrr = analytics.currentMrrMinor(merchantId);
                AnalyticsQueryService.SuccessRate rate = analytics.successRate(merchantId, from, now, null);
                figures =
                        List.of(
                                Figure.money("trailing_tpv_30d", "Trailing TPV (30d)", tpv),
                                Figure.money("mrr", "MRR", mrr),
                                Figure.percent("success_rate_30d", "Payment success rate (30d)", rate.ratePercent()));
                citations = List.of("analytics.tpv (trailing 30d)", "analytics.mrr", "analytics.success-rate (30d)");
                answer =
                        "Over the last 30 days: TPV "
                                + CopilotFormat.inr(tpv)
                                + ", MRR "
                                + CopilotFormat.inr(mrr)
                                + ", success rate "
                                + CopilotFormat.pct(rate.ratePercent())
                                + ".";
            }
        }

        return copilot.respond(
                merchantId, conversationId, CopilotSurface.QUERY, question, FEATURE, figures, citations, answer);
    }

    /** Deterministic keyword intent-matcher (PRD Module 17) — the fallback under the model stand-in. */
    private static Intent detect(String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (contains(q, "arr", "annual recurring", "annual revenue")) {
            return Intent.ARR;
        }
        if (contains(q, "mrr", "recurring", "subscription")) {
            return Intent.MRR;
        }
        if (contains(q, "success", "failure", "failed", "decline", "conversion")) {
            return Intent.SUCCESS_RATE;
        }
        if (contains(q, "cash", "runway", "forecast", "balance", "working capital")) {
            return Intent.CASHFLOW;
        }
        if (contains(q, "tpv", "volume", "revenue", "sales", "processed", "throughput")) {
            return Intent.TPV;
        }
        return Intent.SUMMARY;
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
