package com.qeetgroup.qeetpay.tds;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TDS/TCS tracking API (PRD Module 06): record a tax-at-source deduction, read/list deductions, issue
 * a deductee certificate, and pull the per-section quarterly summary for return filing.
 */
@Tag(
        name = "TDS / TCS",
        description = "Record tax-at-source deductions, issue deductee certificates, and pull per-section quarterly summaries.")
@RestController
@RequestMapping("/v1/tds")
public class TdsController {

    private final TdsService tds;

    public TdsController(TdsService tds) {
        this.tds = tds;
    }

    @PostMapping("/deductions")
    public ResponseEntity<DeductionView> record(@Valid @RequestBody RecordDeductionRequest req) {
        TdsDeduction deduction =
                tds.recordDeduction(
                        MerchantContext.require(), req.kind(), req.section(), req.deducteeName(),
                        req.deducteePan(), req.grossMinor(), req.rateBps(), req.transactionRef(),
                        req.deductedOn());
        return ResponseEntity.ok(DeductionView.of(deduction));
    }

    @GetMapping("/deductions")
    public List<DeductionView> list() {
        return tds.listDeductions(MerchantContext.require()).stream().map(DeductionView::of).toList();
    }

    @GetMapping("/deductions/{deductionId}")
    public DeductionView get(@PathVariable UUID deductionId) {
        return DeductionView.of(tds.getDeduction(MerchantContext.require(), deductionId));
    }

    @PostMapping("/deductions/{deductionId}/certificate")
    public DeductionView issueCertificate(@PathVariable UUID deductionId) {
        return DeductionView.of(tds.issueCertificate(MerchantContext.require(), deductionId));
    }

    @GetMapping("/summary")
    public SummaryView summary(@RequestParam String quarter) {
        return SummaryView.of(tds.quarterlySummary(MerchantContext.require(), quarter));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record RecordDeductionRequest(
            @NotNull TaxKind kind,
            @NotBlank String section,
            @NotBlank String deducteeName,
            String deducteePan,
            @NotNull @Positive Long grossMinor,
            @NotNull @PositiveOrZero Integer rateBps,
            String transactionRef,
            @NotNull LocalDate deductedOn) {}

    public record DeductionView(
            String id, String kind, String section, String deducteeName, String deducteePan,
            long grossMinor, int rateBps, long taxMinor, String transactionRef, LocalDate deductedOn,
            String quarter, String certificateNo, Instant createdAt) {
        static DeductionView of(TdsDeduction d) {
            return new DeductionView(
                    d.getId().toString(), d.getKind().name(), d.getSection(), d.getDeducteeName(),
                    d.getDeducteePan(), d.getGrossMinor(), d.getRateBps(), d.getTaxMinor(),
                    d.getTransactionRef(), d.getDeductedOn(), d.getQuarter(), d.getCertificateNo(),
                    d.getCreatedAt());
        }
    }

    public record SummaryView(
            String quarter, int count, long totalGrossMinor, long totalTaxMinor,
            Map<String, Long> taxBySection) {
        static SummaryView of(TdsService.QuarterlySummary s) {
            return new SummaryView(
                    s.quarter(), s.count(), s.totalGrossMinor(), s.totalTaxMinor(), s.taxBySection());
        }
    }
}
