package com.qeetgroup.qeetpay.crossborder;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outbound / import cross-border API (PRD Module 14.4): quote and pay a foreign vendor/SaaS/cloud in a
 * foreign currency via SWIFT, with LRS financial-year tracking and 2.5% TCS collected above the LRS
 * threshold. Then read remittances and confirm/settle them.
 */
@Tag(
        name = "Cross-Border — Outbound",
        description = "Outbound/import remittances to foreign vendors via SWIFT, with LRS tracking + 2.5% TCS.")
@RestController
@RequestMapping("/v1/crossborder/outbound")
public class OutboundRemittanceController {

    private final OutboundRemittanceService outbound;

    public OutboundRemittanceController(OutboundRemittanceService outbound) {
        this.outbound = outbound;
    }

    @PostMapping("/quote")
    public QuoteView quote(@Valid @RequestBody QuoteRequest req) {
        OutboundRemittanceService.Quote q =
                outbound.quote(MerchantContext.require(), req.currency(), req.foreignAmountMinor());
        return QuoteView.of(q);
    }

    @PostMapping
    public ResponseEntity<RemittanceView> create(@Valid @RequestBody CreateRemittanceRequest req) {
        OutboundRemittance r =
                outbound.create(
                        MerchantContext.require(), req.beneficiaryName(), req.beneficiarySwift(),
                        req.beneficiaryAccount(), req.beneficiaryCountry(), req.currency(),
                        req.foreignAmountMinor(), req.purposeCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(RemittanceView.of(r));
    }

    @GetMapping
    public List<RemittanceView> list() {
        return outbound.list(MerchantContext.require()).stream().map(RemittanceView::of).toList();
    }

    @GetMapping("/{remittanceId}")
    public RemittanceDetailView get(@PathVariable UUID remittanceId) {
        OutboundRemittanceService.RemittanceWithEvents rwe =
                outbound.get(MerchantContext.require(), remittanceId);
        return new RemittanceDetailView(
                RemittanceView.of(rwe.remittance()), rwe.events().stream().map(EventView::of).toList());
    }

    @PostMapping("/{remittanceId}/mark-remitted")
    public RemittanceView markRemitted(
            @PathVariable UUID remittanceId, @Valid @RequestBody MarkRemittedRequest req) {
        return RemittanceView.of(
                outbound.markRemitted(MerchantContext.require(), remittanceId, req.remittanceReference()));
    }

    @PostMapping("/{remittanceId}/mark-failed")
    public RemittanceView markFailed(
            @PathVariable UUID remittanceId, @Valid @RequestBody MarkFailedRequest req) {
        return RemittanceView.of(
                outbound.markFailed(MerchantContext.require(), remittanceId, req.reason()));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record QuoteRequest(@NotBlank String currency, @NotNull @Positive Long foreignAmountMinor) {}

    public record CreateRemittanceRequest(
            @NotBlank String beneficiaryName,
            @NotBlank String beneficiarySwift,
            @NotBlank String beneficiaryAccount,
            @NotBlank String beneficiaryCountry,
            @NotBlank String currency,
            @NotNull @Positive Long foreignAmountMinor,
            @NotBlank String purposeCode) {}

    public record MarkRemittedRequest(@NotBlank String remittanceReference) {}

    public record MarkFailedRequest(@NotBlank String reason) {}

    public record QuoteView(
            String currency, long foreignAmountMinor, BigDecimal fxRate, long principalInrMinor,
            String financialYear, long lrsCumulativeBeforeMinor, long lrsThresholdMinor, long tcsMinor,
            int tcsBps, long inrDebitedMinor) {
        static QuoteView of(OutboundRemittanceService.Quote q) {
            return new QuoteView(
                    q.currency(), q.foreignAmountMinor(), q.fxRate(), q.principalInrMinor(),
                    q.financialYear(), q.lrsCumulativeBeforeMinor(), q.lrsThresholdMinor(), q.tcsMinor(),
                    q.tcsBps(), q.inrDebitedMinor());
        }
    }

    @Schema(name = "OutboundRemittanceView")
    public record RemittanceView(
            String id, String beneficiaryName, String beneficiarySwift, String beneficiaryCountry,
            String purposeCode, String currency, long foreignAmountMinor, BigDecimal fxRate,
            long principalInrMinor, long tcsMinor, long inrDebitedMinor, String financialYear,
            long lrsCumulativeAfterMinor, String status, String ledgerEntryId, String remittanceReference,
            Instant createdAt) {
        static RemittanceView of(OutboundRemittance r) {
            return new RemittanceView(
                    r.getId().toString(), r.getBeneficiaryName(), r.getBeneficiarySwift(),
                    r.getBeneficiaryCountry(), r.getPurposeCode(), r.getCurrency(), r.getForeignAmountMinor(),
                    r.getFxRate(), r.getPrincipalInrMinor(), r.getTcsMinor(), r.getInrDebitedMinor(),
                    r.getFinancialYear(), r.getLrsCumulativeAfterMinor(), r.getStatus().name(),
                    r.getLedgerEntryId().toString(), r.getRemittanceReference(), r.getCreatedAt());
        }
    }

    @Schema(name = "RemittanceEventView")
    public record EventView(String id, String type, long amountMinor, String note, Instant createdAt) {
        static EventView of(OutboundRemittanceEvent e) {
            return new EventView(
                    e.getId().toString(), e.getType().name(), e.getAmountMinor(), e.getNote(), e.getCreatedAt());
        }
    }

    public record RemittanceDetailView(RemittanceView remittance, List<EventView> events) {}
}
