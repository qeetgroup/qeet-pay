package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regulatory-Change Impact Radar (PRD Module 06.5, TAD §7 + §8.2). Stores announced-but-not-yet-effective
 * GST changes and, for a stored change, forecasts which of the merchant's invoices/lines are affected and
 * the estimated tax delta if the change applied to the current supply mix.
 *
 * <p>The impact report is a deterministic computation over real invoice lines, so it is authoritative;
 * it is routed through the {@link AiGateway} (advisory feature {@code gst.reg_change_forecast}) purely
 * for the §6.4 governance guarantees — audit trail, outbox event, merchant RLS scope — with the report
 * itself supplied as the deterministic fallback. Output is explicitly labelled a forecast + confidence.
 */
@Service
public class RegChangeRadar {

    static final String FEATURE = "gst.reg_change_forecast";

    private static final String DISCLAIMER =
            "Forecast estimate — assumes the announced change applies to the merchant's current invoice "
                    + "mix for this HSN/SAC. Not tax advice; confirm against the final GST notification.";

    private final RegulatoryChangeRepository changes;
    private final GstInvoiceLineRepository lines;
    private final GstInvoiceRepository invoices;
    private final AiGateway gateway;
    private final MerchantScope merchantScope;
    private final ObjectMapper objectMapper;

    public RegChangeRadar(
            RegulatoryChangeRepository changes,
            GstInvoiceLineRepository lines,
            GstInvoiceRepository invoices,
            AiGateway gateway,
            MerchantScope merchantScope,
            ObjectMapper objectMapper) {
        this.changes = changes;
        this.lines = lines;
        this.invoices = invoices;
        this.gateway = gateway;
        this.merchantScope = merchantScope;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegulatoryChange recordChange(
            UUID merchantId,
            String hsnSac,
            RegChangeType changeType,
            Integer oldRatePct,
            int newRatePct,
            LocalDate effectiveDate,
            String title,
            String source) {
        merchantScope.apply(merchantId);
        if (hsnSac == null || hsnSac.isBlank()) {
            throw new IllegalArgumentException("hsnSac is required");
        }
        if (newRatePct < 0) {
            throw new IllegalArgumentException("newRatePct must be non-negative");
        }
        if (effectiveDate == null) {
            throw new IllegalArgumentException("effectiveDate is required");
        }
        return changes.save(
                new RegulatoryChange(
                        merchantId, hsnSac.trim(), changeType, oldRatePct, newRatePct, effectiveDate, title, source));
    }

    @Transactional(readOnly = true)
    public List<RegulatoryChange> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return changes.findByMerchantIdOrderByEffectiveDateAsc(merchantId);
    }

    @Transactional(readOnly = true)
    public RegulatoryChange get(UUID merchantId, UUID changeId) {
        merchantScope.apply(merchantId);
        return load(merchantId, changeId);
    }

    /** Forecasts the impact of an announced change over the merchant's existing GST invoices. */
    @Transactional
    public RegChangeImpactReport computeImpact(UUID merchantId, UUID changeId, Set<String> scopes) {
        merchantScope.apply(merchantId);
        RegulatoryChange change = load(merchantId, changeId);

        List<GstInvoiceLine> affected = lines.findByMerchantIdAndHsnSac(merchantId, change.getHsnSac());

        // Group by invoice, accumulating current vs forecast tax.
        Map<UUID, long[]> byInvoice = new LinkedHashMap<>(); // invoiceId -> {taxable, current, forecast}
        long totalTaxable = 0, totalCurrent = 0, totalForecast = 0;
        for (GstInvoiceLine line : affected) {
            long lineCurrent = line.getCgstMinor() + line.getSgstMinor() + line.getIgstMinor();
            long lineForecast = applyRate(line.getTaxableMinor(), change.getNewRatePct());
            long[] agg = byInvoice.computeIfAbsent(line.getInvoiceId(), k -> new long[3]);
            agg[0] += line.getTaxableMinor();
            agg[1] += lineCurrent;
            agg[2] += lineForecast;
            totalTaxable += line.getTaxableMinor();
            totalCurrent += lineCurrent;
            totalForecast += lineForecast;
        }

        List<RegChangeImpactReport.InvoiceImpact> invoiceImpacts = new ArrayList<>();
        for (Map.Entry<UUID, long[]> e : byInvoice.entrySet()) {
            long[] a = e.getValue();
            String number =
                    invoices
                            .findById(e.getKey())
                            .filter(i -> i.getMerchantId().equals(merchantId))
                            .map(GstInvoice::getInvoiceNumber)
                            .orElse(e.getKey().toString());
            invoiceImpacts.add(
                    new RegChangeImpactReport.InvoiceImpact(
                            e.getKey().toString(), number, a[0], a[1], a[2], a[2] - a[1]));
        }

        int affectedInvoiceCount = byInvoice.size();
        double confidence =
                affectedInvoiceCount == 0 ? 0.30 : Math.min(0.90, 0.55 + 0.07 * affectedInvoiceCount);

        // Route through the gateway for the §6.4 audit trail; the deterministic report is authoritative.
        long deltaMinor = totalForecast - totalCurrent;
        String summary =
                "GST reg-change forecast: HSN/SAC " + change.getHsnSac() + " rate → " + change.getNewRatePct()
                        + "%, affects " + affectedInvoiceCount + " invoice(s), delta "
                        + deltaMinor + " paise";
        AiDecisionResult decision =
                gateway.evaluate(
                        new AiRequest(
                                merchantId, FEATURE, null, summary, false, false,
                                scopes == null ? Set.of() : scopes, 0.5),
                        () -> forecastJson(change, affectedInvoiceCount, deltaMinor, confidence));

        return new RegChangeImpactReport(
                change.getId().toString(),
                change.getHsnSac(),
                change.getOldRatePct(),
                change.getNewRatePct(),
                change.getEffectiveDate(),
                true,
                confidence,
                DISCLAIMER,
                affectedInvoiceCount,
                affected.size(),
                totalTaxable,
                totalCurrent,
                totalForecast,
                totalForecast - totalCurrent,
                decision.decisionId().toString(),
                invoiceImpacts);
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private RegulatoryChange load(UUID merchantId, UUID changeId) {
        return changes
                .findById(changeId)
                .filter(c -> c.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new RegulatoryChangeNotFoundException("no regulatory change " + changeId));
    }

    /** taxable · rate%, HALF_UP to whole minor units — the GSTN rounding convention (see GstCalculator). */
    private static long applyRate(long taxableMinor, int ratePct) {
        if (ratePct == 0) {
            return 0;
        }
        return BigDecimal.valueOf(taxableMinor)
                .multiply(BigDecimal.valueOf(ratePct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String forecastJson(
            RegulatoryChange change, int affectedInvoiceCount, long deltaMinor, double confidence) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("changeId", change.getId().toString());
        b.put("hsnSac", change.getHsnSac());
        b.put("newRatePct", change.getNewRatePct());
        b.put("affectedInvoiceCount", affectedInvoiceCount);
        b.put("deltaGstMinor", deltaMinor);
        b.put("confidence", confidence);
        b.put("forecast", true);
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise reg-change forecast", e);
        }
    }
}
