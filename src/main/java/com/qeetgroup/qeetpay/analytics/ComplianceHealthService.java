package com.qeetgroup.qeetpay.analytics;

import com.qeetgroup.qeetpay.filing.FilingService;
import com.qeetgroup.qeetpay.filing.GstReturn;
import com.qeetgroup.qeetpay.kyb.KybService;
import com.qeetgroup.qeetpay.kyb.MerchantKyb;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.config.AppProperties;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import com.qeetgroup.qeetpay.reconciliation.Settlement;
import com.qeetgroup.qeetpay.reconciliation.SettlementService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified Merchant Financial-Health &amp; Compliance Dashboard (PRD Module 12.6, Phase-2 pull-forward).
 * A read-only <em>composition</em> — never a new data-collection effort — that folds four otherwise
 * separate operator surfaces into one composite view:
 *
 * <ol>
 *   <li><b>Settlement / nodal reconciliation health</b> — from the {@code reconciliation} module's
 *       public {@link SettlementService} read API plus the {@code ledger} nodal-balance invariant
 *       (the settlement holding account must never go negative; TAD §6.2).
 *   <li><b>GSTR filing status</b> — from the {@code filing} module's public {@link FilingService}.
 *   <li><b>Fraud posture</b> — the {@code fraud} module is a stateless advisory client (TAD §8): it
 *       persists no decisions, so the honest, available posture is its <em>enablement</em>, read from
 *       the platform {@link AppProperties} that gates the live fraud-svc client.
 *   <li><b>KYB / onboarding status</b> — from the {@code kyb} module's public {@link KybService}.
 * </ol>
 *
 * <p>Headline financial KPIs reuse this module's own {@link AnalyticsQueryService} (MRR/ARR, trailing
 * TPV and success rate). Every tile carries an {@code asOf} timestamp because the underlying data is
 * read on demand, not guaranteed real-time (PRD Module 12.6 edge case) — no tile implies a freshness
 * the architecture does not provide.
 *
 * <p>Strictly read-only: no ledger writes, no mutation of any module. Other domains are read only
 * through their public services; this service never touches another module's tables. Its module
 * dependencies (reconciliation, filing, kyb) are declared in {@code package-info.java}.
 */
@Service
public class ComplianceHealthService {

    /** A composite is "healthy" only when every composed domain is healthy. */
    public static final String HEALTHY = "HEALTHY";

    public static final String ATTENTION = "ATTENTION";

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final AnalyticsQueryService analytics;
    private final SettlementService settlements;
    private final FilingService filing;
    private final KybService kyb;
    private final LedgerService ledger;
    private final AppProperties props;
    private final MerchantScope merchantScope;

    public ComplianceHealthService(
            AnalyticsQueryService analytics,
            SettlementService settlements,
            FilingService filing,
            KybService kyb,
            LedgerService ledger,
            AppProperties props,
            MerchantScope merchantScope) {
        this.analytics = analytics;
        this.settlements = settlements;
        this.filing = filing;
        this.kyb = kyb;
        this.ledger = ledger;
        this.props = props;
        this.merchantScope = merchantScope;
    }

    /** Composes the full dashboard over a trailing {@code windowDays} KPI window (1–365). */
    @Transactional(readOnly = true)
    public ComplianceHealth compose(UUID merchantId, int windowDays) {
        if (windowDays < 1 || windowDays > 365) {
            throw new IllegalArgumentException("windowDays must be between 1 and 365");
        }
        merchantScope.apply(merchantId);
        Instant now = Instant.now();

        ReconHealth recon = reconHealth(merchantId, now);
        FilingSummary filingSummary = filingSummary(merchantId, now);
        FraudPosture fraud = fraudPosture(now);
        KybOnboarding kybStatus = kybOnboarding(merchantId, now);
        FinancialKpis kpis = financialKpis(merchantId, now, windowDays);

        String overall =
                HEALTHY.equals(recon.status())
                                && HEALTHY.equals(filingSummary.status())
                                && kybStatus.onboardingComplete()
                        ? HEALTHY
                        : ATTENTION;

        return new ComplianceHealth(merchantId, now, overall, recon, filingSummary, fraud, kybStatus, kpis);
    }

    // ── Settlement / nodal reconciliation health ──────────────────────────────

    private ReconHealth reconHealth(UUID merchantId, Instant asOf) {
        List<Settlement> all = settlements.list(merchantId);
        int reconciled = 0;
        int discrepancy = 0;
        int pending = 0;
        for (Settlement s : all) {
            switch (s.getStatus()) {
                case RECONCILED -> reconciled++;
                case DISCREPANCY -> discrepancy++;
                case RECEIVED -> pending++;
            }
        }
        // Nodal invariant (TAD §6.2): the settlement holding account must never go negative — we can
        // never settle out more than has been captured for the merchant.
        long nodalBalance =
                ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "settlement").getId());
        boolean nodalHealthy = nodalBalance >= 0;
        String status = (discrepancy == 0 && nodalHealthy) ? HEALTHY : ATTENTION;
        return new ReconHealth(
                all.size(), reconciled, discrepancy, pending, nodalBalance, nodalHealthy, status, asOf);
    }

    // ── GSTR filing status ────────────────────────────────────────────────────

    private FilingSummary filingSummary(UUID merchantId, Instant asOf) {
        List<GstReturn> all = filing.listReturns(merchantId);
        int filed = 0;
        int prepared = 0;
        int draft = 0;
        int error = 0;
        long taxFiled = 0;
        String latestFiledPeriod = null;
        Instant latestFiledAt = null;
        for (GstReturn r : all) {
            switch (r.getStatus()) {
                case FILED -> {
                    filed++;
                    taxFiled += r.getTotalTaxMinor();
                    if (r.getFiledAt() != null
                            && (latestFiledAt == null || r.getFiledAt().isAfter(latestFiledAt))) {
                        latestFiledAt = r.getFiledAt();
                        latestFiledPeriod = r.getPeriod();
                    }
                }
                case PREPARED -> prepared++;
                case DRAFT -> draft++;
                case ERROR -> error++;
            }
        }
        String status = error == 0 ? HEALTHY : ATTENTION;
        return new FilingSummary(
                all.size(), filed, prepared, draft, error, taxFiled, latestFiledPeriod, status, asOf);
    }

    // ── Fraud posture (stateless advisory client — enablement only) ────────────

    private FraudPosture fraudPosture(Instant asOf) {
        boolean enabled = props.getFraud().isEnabled();
        String mode = enabled ? "ACTIVE" : "ADVISORY_DISABLED";
        String description =
                enabled
                        ? "Fraud scoring active via fraud-svc; advisory and fail-open — a scoring outage never blocks payments."
                        : "Fraud scoring disabled (allow-all); decisions are advisory and fail-open by design.";
        return new FraudPosture(enabled, mode, description, asOf);
    }

    // ── KYB / onboarding status ───────────────────────────────────────────────

    private KybOnboarding kybOnboarding(UUID merchantId, Instant asOf) {
        MerchantKyb k = kyb.status(merchantId);
        boolean complete = MerchantKyb.VERIFIED.equals(k.getOverallStatus());
        Instant tileAsOf = k.getVerifiedAt() != null ? k.getVerifiedAt() : asOf;
        return new KybOnboarding(
                k.getOverallStatus(),
                k.getPanStatus(),
                k.getGstinStatus(),
                k.getBankStatus(),
                complete,
                k.getVerifiedAt(),
                tileAsOf);
    }

    // ── Headline financial KPIs (reuse existing analytics queries) ─────────────

    private FinancialKpis financialKpis(UUID merchantId, Instant now, int windowDays) {
        Instant from = now.minus(windowDays, ChronoUnit.DAYS);
        long trailingTpv =
                analytics.tpvByPeriod(merchantId, from, now, "DAY").stream()
                        .mapToLong(AnalyticsQueryService.TpvBucket::totalMinor)
                        .sum();
        AnalyticsQueryService.SuccessRate rate = analytics.successRate(merchantId, from, now, null);
        return new FinancialKpis(
                analytics.currentMrrMinor(merchantId),
                analytics.currentArrMinor(merchantId),
                trailingTpv,
                rate.ratePercent(),
                windowDays,
                now);
    }

    // ── Composite DTO ─────────────────────────────────────────────────────────

    /** The single-screen composite (PRD Module 12.6). */
    public record ComplianceHealth(
            UUID merchantId,
            Instant generatedAt,
            String overallStatus,
            ReconHealth reconciliation,
            FilingSummary filing,
            FraudPosture fraud,
            KybOnboarding kyb,
            FinancialKpis kpis) {}

    /** Settlement batch outcomes + the nodal-account invariant. */
    public record ReconHealth(
            int totalSettlements,
            int reconciledCount,
            int discrepancyCount,
            int pendingCount,
            long nodalBalanceMinor,
            boolean nodalHealthy,
            String status,
            Instant asOf) {}

    /** GSTR return preparation/filing rollup. */
    public record FilingSummary(
            int totalReturns,
            int filedCount,
            int preparedCount,
            int draftCount,
            int errorCount,
            long totalTaxFiledMinor,
            String latestFiledPeriod,
            String status,
            Instant asOf) {}

    /** Fraud-scoring enablement (the fraud module persists no decisions to summarise). */
    public record FraudPosture(boolean scoringEnabled, String mode, String description, Instant asOf) {}

    /** KYB per-check + overall onboarding status. */
    public record KybOnboarding(
            String overallStatus,
            String panStatus,
            String gstinStatus,
            String bankStatus,
            boolean onboardingComplete,
            Instant verifiedAt,
            Instant asOf) {}

    /** Headline money KPIs (paise). */
    public record FinancialKpis(
            long currentMrrMinor,
            long currentArrMinor,
            long trailingTpvMinor,
            double trailingSuccessRatePercent,
            int windowDays,
            Instant asOf) {}
}
