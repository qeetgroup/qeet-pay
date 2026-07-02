package com.qeetgroup.qeetpay.analytics;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Analytics read API — TPV, MRR waterfall, ARR, success rate. */
@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private final AnalyticsQueryService query;

    public AnalyticsController(AnalyticsQueryService query) {
        this.query = query;
    }

    @GetMapping("/tpv")
    public List<AnalyticsQueryService.TpvBucket> tpv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") String granularity) {
        UUID merchantId = MerchantContext.require();
        return query.tpvByPeriod(merchantId, from, to, granularity);
    }

    @GetMapping("/mrr")
    public List<AnalyticsQueryService.MrrWaterfallRow> mrr(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return query.mrrWaterfall(MerchantContext.require(), from, to);
    }

    @GetMapping("/arr")
    public ArrView arr() {
        UUID merchantId = MerchantContext.require();
        long mrrMinor = query.currentMrrMinor(merchantId);
        return new ArrView(mrrMinor, query.currentArrMinor(merchantId));
    }

    @GetMapping("/success-rate")
    public AnalyticsQueryService.SuccessRate successRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String method) {
        return query.successRate(MerchantContext.require(), from, to, method);
    }

    public record ArrView(long mrrMinor, long arrMinor) {}
}
