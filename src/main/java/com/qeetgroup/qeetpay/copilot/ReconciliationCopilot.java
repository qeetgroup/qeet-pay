package com.qeetgroup.qeetpay.copilot;

import com.qeetgroup.qeetpay.ai.AiFeature;
import com.qeetgroup.qeetpay.reconciliation.Discrepancy;
import com.qeetgroup.qeetpay.reconciliation.DiscrepancyType;
import com.qeetgroup.qeetpay.reconciliation.Reconciliation;
import com.qeetgroup.qeetpay.reconciliation.ReconciliationService;
import com.qeetgroup.qeetpay.reconciliation.ReconciliationStatus;
import com.qeetgroup.qeetpay.reconciliation.Settlement;
import com.qeetgroup.qeetpay.reconciliation.SettlementService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliation Copilot (PRD N7). Explains settlement reconciliation breaks and estimated leakage by
 * reading the {@code reconciliation} module's public reads — the merchant's settlements, each run's
 * outcome, and the flagged discrepancies — then routing a plain-English explanation through the
 * {@link com.qeetgroup.qeetpay.ai.AiGateway} (via {@link CopilotService}). The deterministic summary is
 * the audited fallback and the answer surfaced under the offline sandbox model stand-in. Every answer
 * cites the breakdown it used.
 *
 * <p>Leakage is a conservative estimate over the flagged discrepancies (never a ledger mutation): an
 * amount mismatch contributes {@code |reported − expected|}; a settled line missing/duplicated/uncaptured
 * in the ledger contributes its reported amount; the batch-total gap contributes its difference. The
 * nodal-imbalance flag is a balance signal, not per-line leakage, so it is reported but not summed.
 */
@Service
public class ReconciliationCopilot {

    static final String FEATURE = AiFeature.RECONCILIATION_COPILOT.key();
    private static final int MAX_EXAMPLES = 3;

    private final SettlementService settlements;
    private final ReconciliationService reconciliation;
    private final CopilotService copilot;

    public ReconciliationCopilot(
            SettlementService settlements, ReconciliationService reconciliation, CopilotService copilot) {
        this.settlements = settlements;
        this.reconciliation = reconciliation;
        this.copilot = copilot;
    }

    @Transactional
    public CopilotAnswer ask(UUID merchantId, UUID conversationId, String question) {
        List<Settlement> all = settlements.list(merchantId);
        int total = all.size();
        int clean = 0;
        int withBreaks = 0;
        int totalBreaks = 0;
        long leakageMinor = 0;
        EnumMap<DiscrepancyType, Integer> byType = new EnumMap<>(DiscrepancyType.class);
        List<String> examples = new ArrayList<>();

        for (Settlement settlement : all) {
            Optional<Reconciliation> run = reconciliation.forSettlement(merchantId, settlement.getId());
            if (run.isEmpty()) {
                continue;
            }
            Reconciliation recon = run.get();
            if (recon.getStatus() == ReconciliationStatus.MATCHED) {
                clean++;
                continue;
            }
            withBreaks++;
            List<Discrepancy> found = reconciliation.discrepanciesOf(merchantId, recon.getId());
            totalBreaks += found.size();
            for (Discrepancy d : found) {
                byType.merge(d.getType(), 1, Integer::sum);
                leakageMinor += leakageOf(d);
                if (examples.size() < MAX_EXAMPLES) {
                    examples.add(describe(d));
                }
            }
        }

        List<Figure> figures = new ArrayList<>();
        figures.add(Figure.count("total_settlements", "Settlements", total));
        figures.add(Figure.count("clean_settlements", "Reconciled cleanly", clean));
        figures.add(Figure.count("settlements_with_breaks", "Settlements with breaks", withBreaks));
        figures.add(Figure.count("total_discrepancies", "Total discrepancies", totalBreaks));
        figures.add(Figure.money("estimated_leakage", "Estimated leakage", leakageMinor));
        for (Map.Entry<DiscrepancyType, Integer> e : byType.entrySet()) {
            figures.add(
                    Figure.count(
                            "break_" + e.getKey().name().toLowerCase(java.util.Locale.ROOT),
                            label(e.getKey()),
                            e.getValue()));
        }

        List<String> citations = List.of("reconciliation.settlements", "reconciliation.discrepancies");

        String answer = summarise(total, clean, withBreaks, totalBreaks, leakageMinor, byType, examples);
        return copilot.respond(
                merchantId,
                conversationId,
                CopilotSurface.RECONCILIATION,
                question,
                FEATURE,
                List.copyOf(figures),
                citations,
                answer);
    }

    private static long leakageOf(Discrepancy d) {
        Long expected = d.getExpectedMinor();
        Long reported = d.getReportedMinor();
        return switch (d.getType()) {
            case AMOUNT_MISMATCH, BATCH_TOTAL_MISMATCH ->
                    (expected != null && reported != null) ? Math.abs(reported - expected) : 0L;
            case MISSING_IN_LEDGER, DUPLICATE_SETTLEMENT, STATUS_NOT_CAPTURED ->
                    reported != null ? reported : 0L;
            case NODAL_IMBALANCE -> 0L; // a balance signal, not per-line leakage
        };
    }

    private static String describe(Discrepancy d) {
        String ref =
                d.getPaymentId() != null
                        ? "payment " + d.getPaymentId()
                        : (d.getProviderPaymentId() != null ? "provider ref " + d.getProviderPaymentId() : "batch-level");
        return label(d.getType()) + " (" + ref + "): " + d.getDetail();
    }

    private static String summarise(
            int total,
            int clean,
            int withBreaks,
            int totalBreaks,
            long leakageMinor,
            EnumMap<DiscrepancyType, Integer> byType,
            List<String> examples) {
        if (total == 0) {
            return "No settlements have been ingested yet, so there are no reconciliation breaks to explain.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(clean)
                .append(" of ")
                .append(total)
                .append(" settlements reconciled cleanly. ");
        if (totalBreaks == 0) {
            sb.append("No discrepancies were flagged — every settled line ties out against the ledger.");
            return sb.toString();
        }
        sb.append(withBreaks)
                .append(withBreaks == 1 ? " settlement is" : " settlements are")
                .append(" flagged with ")
                .append(totalBreaks)
                .append(totalBreaks == 1 ? " discrepancy" : " discrepancies")
                .append(", with an estimated leakage of ")
                .append(CopilotFormat.inr(leakageMinor))
                .append(". ");
        if (!byType.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<DiscrepancyType, Integer> e : byType.entrySet()) {
                parts.add(e.getValue() + "× " + label(e.getKey()));
            }
            sb.append("Breakdown: ").append(String.join(", ", parts)).append(". ");
        }
        if (!examples.isEmpty()) {
            sb.append("For example — ").append(String.join("; ", examples)).append('.');
        }
        return sb.toString();
    }

    private static String label(DiscrepancyType type) {
        return switch (type) {
            case MISSING_IN_LEDGER -> "missing in ledger";
            case STATUS_NOT_CAPTURED -> "payment not captured";
            case AMOUNT_MISMATCH -> "amount mismatch";
            case DUPLICATE_SETTLEMENT -> "duplicate settlement";
            case BATCH_TOTAL_MISMATCH -> "batch-total mismatch";
            case NODAL_IMBALANCE -> "nodal-account imbalance";
        };
    }
}
