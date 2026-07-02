package com.qeetgroup.qeetpay.analytics;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side — aggregation queries over analytics hypertables.
 * Uses JdbcClient (Spring 6.1+) for native SQL aggregations; avoids JPA overhead.
 */
@Service
public class AnalyticsQueryService {

    private final JdbcClient jdbc;
    private final MerchantScope merchantScope;

    public AnalyticsQueryService(JdbcClient jdbc, MerchantScope merchantScope) {
        this.jdbc          = jdbc;
        this.merchantScope = merchantScope;
    }

    // ── TPV (total payment volume) ────────────────────────────────────────────

    /**
     * Returns TPV buckets (CAPTURED only) grouped by the requested granularity.
     * Granularity: DAY | WEEK | MONTH
     */
    @Transactional(readOnly = true)
    public List<TpvBucket> tpvByPeriod(UUID merchantId, Instant from, Instant to, String granularity) {
        merchantScope.apply(merchantId);
        String trunc = switch (granularity.toUpperCase()) {
            case "WEEK"  -> "week";
            case "MONTH" -> "month";
            default      -> "day";
        };
        return jdbc.sql("""
                SELECT date_trunc(:trunc, occurred_at) AS period,
                       COALESCE(SUM(amount_minor), 0)  AS total_minor,
                       COUNT(*)                         AS tx_count
                FROM analytics.payment_events
                WHERE merchant_id = :mid
                  AND event_type  = 'CAPTURED'
                  AND occurred_at BETWEEN :from AND :to
                GROUP BY 1
                ORDER BY 1
                """)
                .param("trunc", trunc)
                .param("mid",   merchantId)
                .param("from",  Timestamp.from(from))
                .param("to",    Timestamp.from(to))
                .query((rs, n) -> new TpvBucket(
                        rs.getTimestamp("period").toInstant(),
                        rs.getLong("total_minor"),
                        rs.getLong("tx_count")))
                .list();
    }

    // ── Payment success rate ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SuccessRate successRate(UUID merchantId, Instant from, Instant to, String method) {
        merchantScope.apply(merchantId);
        // Two queries to avoid Postgres null-type inference issue with optional filter.
        String sql = method == null
                ? """
                  SELECT SUM(CASE WHEN event_type='CAPTURED' THEN 1 ELSE 0 END) AS captured,
                         SUM(CASE WHEN event_type='FAILED'   THEN 1 ELSE 0 END) AS failed
                  FROM analytics.payment_events
                  WHERE merchant_id = :mid AND occurred_at BETWEEN :from AND :to
                  """
                : """
                  SELECT SUM(CASE WHEN event_type='CAPTURED' THEN 1 ELSE 0 END) AS captured,
                         SUM(CASE WHEN event_type='FAILED'   THEN 1 ELSE 0 END) AS failed
                  FROM analytics.payment_events
                  WHERE merchant_id = :mid AND method = :method AND occurred_at BETWEEN :from AND :to
                  """;
        var q = jdbc.sql(sql)
                .param("mid",  merchantId)
                .param("from", Timestamp.from(from))
                .param("to",   Timestamp.from(to));
        if (method != null) q = q.param("method", method);
        var row = q.query((rs, n) -> Map.of(
                        "captured", rs.getLong("captured"),
                        "failed",   rs.getLong("failed")))
                .single();
        long captured = (long) row.get("captured");
        long failed   = (long) row.get("failed");
        long total    = captured + failed;
        double rate   = total == 0 ? 0.0 : (double) captured / total * 100.0;
        return new SuccessRate(captured, failed, rate);
    }

    // ── MRR / ARR ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public long currentMrrMinor(UUID merchantId) {
        merchantScope.apply(merchantId);
        Long mrr = jdbc.sql("""
                SELECT COALESCE(SUM(mrr_delta_minor), 0)
                FROM analytics.subscription_events
                WHERE merchant_id = :mid
                  AND occurred_at <= now()
                """)
                .param("mid", merchantId)
                .query(Long.class)
                .single();
        return mrr == null ? 0L : mrr;
    }

    @Transactional(readOnly = true)
    public long currentArrMinor(UUID merchantId) {
        return currentMrrMinor(merchantId) * 12;
    }

    /** MRR waterfall per calendar month: new, expansion, contraction, churn, net. */
    @Transactional(readOnly = true)
    public List<MrrWaterfallRow> mrrWaterfall(UUID merchantId, Instant from, Instant to) {
        merchantScope.apply(merchantId);
        return jdbc.sql("""
                SELECT date_trunc('month', occurred_at)                                AS period,
                  COALESCE(SUM(CASE WHEN event_type = 'NEW'          THEN mrr_delta_minor ELSE 0 END), 0) AS new_mrr,
                  COALESCE(SUM(CASE WHEN event_type = 'EXPANSION'    THEN mrr_delta_minor ELSE 0 END), 0) AS expansion,
                  COALESCE(SUM(CASE WHEN event_type = 'CONTRACTION'  THEN mrr_delta_minor ELSE 0 END), 0) AS contraction,
                  COALESCE(SUM(CASE WHEN event_type = 'CHURN'        THEN mrr_delta_minor ELSE 0 END), 0) AS churn,
                  COALESCE(SUM(CASE WHEN event_type = 'REACTIVATION' THEN mrr_delta_minor ELSE 0 END), 0) AS reactivation,
                  COALESCE(SUM(mrr_delta_minor), 0)                                   AS net_change
                FROM analytics.subscription_events
                WHERE merchant_id = :mid
                  AND occurred_at BETWEEN :from AND :to
                GROUP BY 1
                ORDER BY 1
                """)
                .param("mid",  merchantId)
                .param("from", Timestamp.from(from))
                .param("to",   Timestamp.from(to))
                .query((rs, n) -> new MrrWaterfallRow(
                        rs.getTimestamp("period").toInstant(),
                        rs.getLong("new_mrr"),
                        rs.getLong("expansion"),
                        rs.getLong("contraction"),
                        rs.getLong("churn"),
                        rs.getLong("reactivation"),
                        rs.getLong("net_change")))
                .list();
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    public record TpvBucket(Instant period, long totalMinor, long txCount) {}
    public record SuccessRate(long captured, long failed, double ratePercent) {}
    public record MrrWaterfallRow(Instant period, long newMrr, long expansion,
                                  long contraction, long churn, long reactivation, long netChange) {}
}
