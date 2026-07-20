package com.qeetgroup.qeetpay.aml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AML / CFT orchestrator (PRD §7.7). Ties the pure engines ({@link TransactionMonitor},
 * {@link MuleScorer}) and pluggable adapters ({@link SanctionsListAdapter}, {@link FiuFilingAdapter})
 * to persistence + the transactional outbox. Every write scopes the DB session to the merchant via
 * {@link MerchantScope} so Postgres RLS isolates it; alerts and STR filings are published to the
 * outbox in the same transaction.
 */
@Service
public class AmlService {

    private final SanctionScreeningRepository screenings;
    private final AmlAlertRepository alerts;
    private final AmlCaseRepository cases;
    private final StrReportRepository strReports;
    private final SanctionsListAdapter sanctions;
    private final FiuFilingAdapter fiu;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public AmlService(
            SanctionScreeningRepository screenings,
            AmlAlertRepository alerts,
            AmlCaseRepository cases,
            StrReportRepository strReports,
            SanctionsListAdapter sanctions,
            FiuFilingAdapter fiu,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.screenings = screenings;
        this.alerts = alerts;
        this.cases = cases;
        this.strReports = strReports;
        this.sanctions = sanctions;
        this.fiu = fiu;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    // ── Sanctions / PEP screening ────────────────────────────────────────────

    /** Screens a party against OFAC/UN/PEP lists; persists the result and raises an alert on a hit. */
    @Transactional
    public SanctionScreening screenParty(
            UUID merchantId, PartyType partyType, String partyName, String identifier) {
        merchantScope.apply(merchantId);
        if (partyName == null || partyName.isBlank()) {
            throw new IllegalArgumentException("partyName is required");
        }
        List<SanctionMatch> matches = sanctions.screen(partyName, identifier);
        ScreeningResult result = matches.isEmpty() ? ScreeningResult.CLEAR : ScreeningResult.HIT;
        int riskScore = matches.stream().mapToInt(SanctionMatch::score).max().orElse(0);

        SanctionScreening screening =
                screenings.save(
                        new SanctionScreening(
                                merchantId, partyType, partyName, identifier, result,
                                matches.size(), riskScore, write(matches)));

        if (result == ScreeningResult.HIT) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("screeningId", screening.getId().toString());
            detail.put("partyName", partyName);
            detail.put("identifier", identifier);
            detail.put("matches", matches);
            raiseAlert(merchantId, partyName, null, "AML-SANCT-01", "SANCTIONS_SCREENING", riskScore, detail);
        }
        return screening;
    }

    @Transactional(readOnly = true)
    public List<SanctionScreening> listScreenings(UUID merchantId) {
        merchantScope.apply(merchantId);
        return screenings.findByMerchantIdOrderByScreenedAtDesc(merchantId);
    }

    // ── Transaction monitoring ───────────────────────────────────────────────

    /** Evaluates a transaction against the rules engine and persists one alert per rule that fires. */
    @Transactional
    public List<AmlAlert> monitorTransaction(UUID merchantId, TransactionSignal signal) {
        merchantScope.apply(merchantId);
        List<RuleHit> hits = TransactionMonitor.evaluate(signal);
        String subject = signal.beneficiaryRef() != null ? signal.beneficiaryRef() : signal.transactionRef();
        List<AmlAlert> raised = new ArrayList<>(hits.size());
        for (RuleHit hit : hits) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", hit.detail());
            detail.put("amountMinor", signal.amountMinor());
            detail.put("currency", signal.currency());
            raised.add(
                    raiseAlert(
                            merchantId, subject, signal.transactionRef(), hit.ruleCode(),
                            hit.category(), hit.riskScore(), detail));
        }
        return raised;
    }

    // ── Mule-account detection ───────────────────────────────────────────────

    /** Scores a payout beneficiary for mule behaviour; raises an alert when it crosses the threshold. */
    @Transactional
    public MuleAssessment assessBeneficiary(UUID merchantId, BeneficiaryActivity activity) {
        merchantScope.apply(merchantId);
        MuleAssessment assessment = MuleScorer.score(activity);
        if (assessment.flagged()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("beneficiaryRef", assessment.beneficiaryRef());
            detail.put("reasons", assessment.reasons());
            raiseAlert(
                    merchantId, assessment.beneficiaryRef(), null, "AML-MULE-01", "MULE_ACCOUNT",
                    assessment.riskScore(), detail);
        }
        return assessment;
    }

    // ── Alerts ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AmlAlert> listAlerts(UUID merchantId, AlertStatus status) {
        merchantScope.apply(merchantId);
        return status == null
                ? alerts.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                : alerts.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status);
    }

    private AmlAlert raiseAlert(
            UUID merchantId,
            String subjectRef,
            String transactionRef,
            String ruleCode,
            String category,
            int riskScore,
            Map<String, Object> detail) {
        AmlAlert alert =
                alerts.save(
                        new AmlAlert(
                                merchantId, subjectRef, transactionRef, ruleCode, category,
                                riskScore, write(detail)));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("alertId", alert.getId().toString());
        event.put("ruleCode", ruleCode);
        event.put("category", category);
        event.put("riskScore", riskScore);
        event.put("severity", alert.getSeverity().name());
        event.put("subjectRef", subjectRef);
        outbox.enqueue(merchantId, "aml.alert.raised", write(event));
        return alert;
    }

    // ── Cases ────────────────────────────────────────────────────────────────

    /** Opens a case, optionally attaching (escalating) the given alerts to it. */
    @Transactional
    public AmlCase createCase(UUID merchantId, String subject, String description, List<UUID> alertIds) {
        merchantScope.apply(merchantId);
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        AmlCase amlCase = cases.save(new AmlCase(merchantId, subject, description));

        int maxScore = 0;
        int attached = 0;
        if (alertIds != null) {
            for (UUID alertId : alertIds) {
                AmlAlert alert = loadAlert(merchantId, alertId);
                alert.attachToCase(amlCase.getId());
                alerts.save(alert);
                maxScore = Math.max(maxScore, alert.getRiskScore());
                attached++;
            }
        }
        amlCase.recordAlerts(attached, maxScore);
        cases.save(amlCase);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("caseId", amlCase.getId().toString());
        event.put("subject", subject);
        event.put("alertCount", attached);
        outbox.enqueue(merchantId, "aml.case.opened", write(event));
        return amlCase;
    }

    @Transactional(readOnly = true)
    public List<AmlCase> listCases(UUID merchantId) {
        merchantScope.apply(merchantId);
        return cases.findByMerchantIdOrderByOpenedAtDesc(merchantId);
    }

    @Transactional
    public AmlCase closeCase(UUID merchantId, UUID caseId, CaseDisposition disposition) {
        merchantScope.apply(merchantId);
        AmlCase amlCase = loadCase(merchantId, caseId);
        amlCase.close(disposition);
        cases.save(amlCase);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("caseId", amlCase.getId().toString());
        event.put("disposition", disposition.name());
        outbox.enqueue(merchantId, "aml.case.closed", write(event));
        return amlCase;
    }

    // ── STR reports ──────────────────────────────────────────────────────────

    /**
     * Generates an FIU-IND-style STR. Created DRAFT; when {@code fileImmediately} it is filed through
     * {@link FiuFilingAdapter} (which assigns the reference id) and an {@code aml.str.filed} event is
     * emitted.
     */
    @Transactional
    public StrReport createStrReport(
            UUID merchantId, UUID caseId, String subject, String grounds, boolean fileImmediately) {
        merchantScope.apply(merchantId);
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (grounds == null || grounds.isBlank()) {
            throw new IllegalArgumentException("grounds of suspicion are required");
        }
        if (caseId != null) {
            loadCase(merchantId, caseId); // validate ownership
        }

        String payload = buildFiuPayload(merchantId, caseId, subject, grounds);
        StrReport report = strReports.save(new StrReport(merchantId, caseId, subject, grounds, payload));
        outbox.enqueue(merchantId, "aml.str.created", strEvent(report));

        if (fileImmediately) {
            String reference = fiu.file(merchantId, payload);
            report.markFiled(reference);
            strReports.save(report);
            outbox.enqueue(merchantId, "aml.str.filed", strEvent(report));
        }
        return report;
    }

    @Transactional(readOnly = true)
    public List<StrReport> listStrReports(UUID merchantId) {
        merchantScope.apply(merchantId);
        return strReports.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /** Builds the FIU-IND-style report body (PMLA STR structure). */
    private String buildFiuPayload(UUID merchantId, UUID caseId, String subject, String grounds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportType", "STR");
        body.put("regulator", "FIU-IND");
        body.put("statute", "PMLA");
        Map<String, Object> reportingEntity = new LinkedHashMap<>();
        reportingEntity.put("merchantId", merchantId.toString());
        body.put("reportingEntity", reportingEntity);
        body.put("caseId", caseId == null ? null : caseId.toString());
        body.put("subject", subject);
        body.put("groundsOfSuspicion", grounds);
        body.put("generatedAt", java.time.Instant.now().toString());
        return write(body);
    }

    private String strEvent(StrReport report) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("strReportId", report.getId().toString());
        event.put("status", report.getStatus().name());
        event.put("fiuReferenceId", report.getFiuReferenceId());
        return write(event);
    }

    private AmlCase loadCase(UUID merchantId, UUID caseId) {
        return cases.findById(caseId)
                .filter(c -> c.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new AmlCaseNotFoundException("no case " + caseId));
    }

    private AmlAlert loadAlert(UUID merchantId, UUID alertId) {
        return alerts.findById(alertId)
                .filter(a -> a.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new AmlAlertNotFoundException("no alert " + alertId));
    }

    private String write(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise AML payload", e);
        }
    }
}
