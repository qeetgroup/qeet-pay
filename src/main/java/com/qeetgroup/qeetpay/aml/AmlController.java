package com.qeetgroup.qeetpay.aml;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
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
 * AML / CFT API (PRD §7.7): screen a party against sanctions/PEP lists, monitor a transaction for
 * suspicious patterns, score payout beneficiaries for mule behaviour, manage alerts + investigation
 * cases, and generate/file Suspicious Transaction Reports with FIU-IND.
 */
@Tag(
        name = "AML",
        description =
                "Sanctions/PEP screening, transaction monitoring, mule-account detection, and STR "
                        + "generation/filing with FIU-IND.")
@RestController
@RequestMapping("/v1/aml")
public class AmlController {

    private final AmlService aml;

    public AmlController(AmlService aml) {
        this.aml = aml;
    }

    // ── Screening ──────────────────────────────────────────────────────────

    @PostMapping("/screen")
    public ResponseEntity<ScreeningView> screen(@Valid @RequestBody ScreenRequest req) {
        SanctionScreening s =
                aml.screenParty(MerchantContext.require(), req.partyType(), req.partyName(), req.identifier());
        return ResponseEntity.ok(ScreeningView.of(s));
    }

    // ── Transaction monitoring ────────────────────────────────────────────────

    @PostMapping("/monitor")
    public MonitorResult monitor(@Valid @RequestBody MonitorRequest req) {
        List<AmlAlert> raised =
                aml.monitorTransaction(
                        MerchantContext.require(),
                        new TransactionSignal(
                                req.transactionRef(), req.amountMinor(), req.currency(), req.mcc(),
                                req.countryCode(), req.txnCount24h(), req.amount24hMinor(),
                                req.beneficiaryRef()));
        return new MonitorResult(raised.size(), raised.stream().map(AlertView::of).toList());
    }

    // ── Mule-account detection ────────────────────────────────────────────────

    @PostMapping("/mule-scan")
    public MuleView muleScan(@Valid @RequestBody MuleScanRequest req) {
        MuleAssessment a =
                aml.assessBeneficiary(
                        MerchantContext.require(),
                        new BeneficiaryActivity(
                                req.beneficiaryRef(), req.inboundMinor(), req.outboundMinor(),
                                req.inboundCount(), req.outboundCount(), req.medianHoldSeconds(),
                                req.distinctCounterparties()));
        return MuleView.of(a);
    }

    // ── Alerts ─────────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    public List<AlertView> alerts(@RequestParam(required = false) AlertStatus status) {
        return aml.listAlerts(MerchantContext.require(), status).stream().map(AlertView::of).toList();
    }

    // ── Cases ──────────────────────────────────────────────────────────────

    @PostMapping("/cases")
    public ResponseEntity<CaseView> createCase(@Valid @RequestBody CreateCaseRequest req) {
        AmlCase c =
                aml.createCase(
                        MerchantContext.require(), req.subject(), req.description(), req.alertIds());
        return ResponseEntity.ok(CaseView.of(c));
    }

    @GetMapping("/cases")
    public List<CaseView> cases() {
        return aml.listCases(MerchantContext.require()).stream().map(CaseView::of).toList();
    }

    @PostMapping("/cases/{id}/close")
    public CaseView closeCase(@PathVariable UUID id, @Valid @RequestBody CloseCaseRequest req) {
        return CaseView.of(aml.closeCase(MerchantContext.require(), id, req.disposition()));
    }

    // ── STR reports ────────────────────────────────────────────────────────

    @PostMapping("/str-reports")
    public ResponseEntity<StrView> createStr(@Valid @RequestBody CreateStrRequest req) {
        StrReport r =
                aml.createStrReport(
                        MerchantContext.require(), req.caseId(), req.subject(), req.grounds(),
                        req.fileImmediately() == null || req.fileImmediately());
        return ResponseEntity.ok(StrView.of(r));
    }

    @GetMapping("/str-reports")
    public List<StrView> strReports() {
        return aml.listStrReports(MerchantContext.require()).stream().map(StrView::of).toList();
    }

    // ── Requests ───────────────────────────────────────────────────────────

    public record ScreenRequest(
            @NotNull PartyType partyType, @NotBlank String partyName, String identifier) {}

    public record MonitorRequest(
            @NotBlank String transactionRef,
            @NotNull @Positive Long amountMinor,
            @NotBlank String currency,
            Integer mcc,
            String countryCode,
            Integer txnCount24h,
            Long amount24hMinor,
            String beneficiaryRef) {}

    public record MuleScanRequest(
            @NotBlank String beneficiaryRef,
            long inboundMinor,
            long outboundMinor,
            int inboundCount,
            int outboundCount,
            long medianHoldSeconds,
            int distinctCounterparties) {}

    public record CreateCaseRequest(
            @NotBlank String subject, String description, List<UUID> alertIds) {}

    public record CloseCaseRequest(@NotNull CaseDisposition disposition) {}

    public record CreateStrRequest(
            UUID caseId, @NotBlank String subject, @NotBlank String grounds, Boolean fileImmediately) {}

    // ── Views ──────────────────────────────────────────────────────────────

    public record ScreeningView(
            String id, String partyType, String partyName, String identifier, String result,
            int matchCount, int riskScore, Instant screenedAt) {
        static ScreeningView of(SanctionScreening s) {
            return new ScreeningView(
                    s.getId().toString(), s.getPartyType().name(), s.getPartyName(), s.getIdentifier(),
                    s.getResult().name(), s.getMatchCount(), s.getRiskScore(), s.getScreenedAt());
        }
    }

    public record AlertView(
            String id, String subjectRef, String transactionRef, String ruleCode, String category,
            int riskScore, String severity, String status, String caseId, Instant createdAt) {
        static AlertView of(AmlAlert a) {
            return new AlertView(
                    a.getId().toString(), a.getSubjectRef(), a.getTransactionRef(), a.getRuleCode(),
                    a.getCategory(), a.getRiskScore(), a.getSeverity().name(), a.getStatus().name(),
                    a.getCaseId() == null ? null : a.getCaseId().toString(), a.getCreatedAt());
        }
    }

    public record MonitorResult(int alertCount, List<AlertView> alerts) {}

    public record MuleView(
            String beneficiaryRef, int riskScore, String severity, boolean flagged, List<String> reasons) {
        static MuleView of(MuleAssessment a) {
            return new MuleView(
                    a.beneficiaryRef(), a.riskScore(), a.severity().name(), a.flagged(), a.reasons());
        }
    }

    public record CaseView(
            String id, String subject, String description, String status, String disposition,
            int riskScore, int alertCount, Instant openedAt, Instant closedAt) {
        static CaseView of(AmlCase c) {
            return new CaseView(
                    c.getId().toString(), c.getSubject(), c.getDescription(), c.getStatus().name(),
                    c.getDisposition() == null ? null : c.getDisposition().name(), c.getRiskScore(),
                    c.getAlertCount(), c.getOpenedAt(), c.getClosedAt());
        }
    }

    public record StrView(
            String id, String caseId, String subject, String grounds, String status,
            String fiuReferenceId, Instant createdAt, Instant filedAt) {
        static StrView of(StrReport r) {
            return new StrView(
                    r.getId().toString(), r.getCaseId() == null ? null : r.getCaseId().toString(),
                    r.getSubject(), r.getGrounds(), r.getStatus().name(), r.getFiuReferenceId(),
                    r.getCreatedAt(), r.getFiledAt());
        }
    }
}
