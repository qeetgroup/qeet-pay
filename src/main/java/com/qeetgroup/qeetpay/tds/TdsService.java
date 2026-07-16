package com.qeetgroup.qeetpay.tds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TDS/TCS tracking (PRD Module 06). Recording a deduction computes the tax at source via {@link
 * TdsCalculator}, stamps the Indian financial-year quarter of the deduction date, and persists the
 * fact; issuing a certificate assigns a deterministic certificate number; the quarterly summary
 * aggregates a quarter's deductions with a per-section tax breakdown for return filing. This is a
 * pure compliance ledger — like {@code filing}, it records tax facts and posts <em>no</em> money
 * movement. All writes are outbox-published. Merchant-scoped via platform RLS.
 */
@Service
public class TdsService {

    private final TdsDeductionRepository deductions;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public TdsService(
            TdsDeductionRepository deductions,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.deductions = deductions;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Records a tax-at-source deduction, computing the tax and deriving its FY quarter. */
    @Transactional
    public TdsDeduction recordDeduction(
            UUID merchantId, TaxKind kind, String section, String deducteeName, String deducteePan,
            long grossMinor, int rateBps, String transactionRef, LocalDate deductedOn) {
        merchantScope.apply(merchantId);
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("section is required");
        }
        if (deducteeName == null || deducteeName.isBlank()) {
            throw new IllegalArgumentException("deducteeName is required");
        }
        if (deductedOn == null) {
            throw new IllegalArgumentException("deductedOn is required");
        }
        long taxMinor = TdsCalculator.tax(grossMinor, rateBps);
        String quarter = TdsCalculator.quarterOf(deductedOn);

        TdsDeduction deduction =
                deductions.save(
                        new TdsDeduction(
                                merchantId, kind, section, deducteeName, deducteePan, grossMinor,
                                rateBps, taxMinor, transactionRef, deductedOn, quarter));
        outbox.enqueue(merchantId, "tds.deducted", deductionJson(deduction));
        return deduction;
    }

    /** Issues the deductee's certificate with a deterministic number; fails if already issued. */
    @Transactional
    public TdsDeduction issueCertificate(UUID merchantId, UUID deductionId) {
        merchantScope.apply(merchantId);
        TdsDeduction deduction = load(merchantId, deductionId);
        String idShort = deduction.getId().toString().substring(0, 8).toUpperCase();
        String certificateNo = "QP/" + deduction.getKind() + "/" + deduction.getSection() + "/" + idShort;
        deduction.issueCertificate(certificateNo);
        deductions.save(deduction);
        outbox.enqueue(merchantId, "tds.certificate.issued", certificateJson(deduction));
        return deduction;
    }

    /** Aggregates a quarter's deductions: count, gross/tax totals, and per-section tax breakdown. */
    @Transactional(readOnly = true)
    public QuarterlySummary quarterlySummary(UUID merchantId, String quarter) {
        merchantScope.apply(merchantId);
        if (quarter == null || quarter.isBlank()) {
            throw new IllegalArgumentException("quarter is required");
        }
        List<TdsDeduction> rows =
                deductions.findByMerchantIdAndQuarterOrderByCreatedAtDesc(merchantId, quarter);
        long totalGross = 0, totalTax = 0;
        Map<String, Long> taxBySection = new LinkedHashMap<>();
        for (TdsDeduction d : rows) {
            totalGross += d.getGrossMinor();
            totalTax += d.getTaxMinor();
            taxBySection.merge(d.getSection(), d.getTaxMinor(), Long::sum);
        }
        return new QuarterlySummary(quarter, rows.size(), totalGross, totalTax, taxBySection);
    }

    @Transactional(readOnly = true)
    public List<TdsDeduction> listDeductions(UUID merchantId) {
        merchantScope.apply(merchantId);
        return deductions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public TdsDeduction getDeduction(UUID merchantId, UUID deductionId) {
        merchantScope.apply(merchantId);
        return load(merchantId, deductionId);
    }

    private TdsDeduction load(UUID merchantId, UUID deductionId) {
        return deductions
                .findById(deductionId)
                .filter(d -> d.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new TdsNotFoundException("no deduction " + deductionId));
    }

    /** A quarter's totals plus per-section tax (keyed by section, e.g. "194J", "52"). */
    public record QuarterlySummary(
            String quarter, int count, long totalGrossMinor, long totalTaxMinor,
            Map<String, Long> taxBySection) {}

    private String deductionJson(TdsDeduction d) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("deductionId", d.getId().toString());
        b.put("kind", d.getKind().name());
        b.put("section", d.getSection());
        b.put("grossMinor", d.getGrossMinor());
        b.put("rateBps", d.getRateBps());
        b.put("taxMinor", d.getTaxMinor());
        b.put("quarter", d.getQuarter());
        return write(b);
    }

    private String certificateJson(TdsDeduction d) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("deductionId", d.getId().toString());
        b.put("kind", d.getKind().name());
        b.put("section", d.getSection());
        b.put("certificateNo", d.getCertificateNo());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise TDS event", e);
        }
    }
}
