package com.qeetgroup.qeetpay.revrec;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
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
 * Revenue-recognition API (TAD §5 "RevRec"): create a schedule (defers revenue), recognise due
 * periods, and read schedules with their per-period slices.
 */
@Tag(
        name = "Revenue Recognition",
        description = "IndAS 115 revenue schedules — defer revenue, recognise due periods, and read schedules with their slices.")
@RestController
@RequestMapping("/v1/revrec/schedules")
public class RevRecController {

    private final RevRecService revRec;

    public RevRecController(RevRecService revRec) {
        this.revRec = revRec;
    }

    @PostMapping
    public ResponseEntity<ScheduleView> create(@Valid @RequestBody CreateScheduleRequest req) {
        UUID merchantId = MerchantContext.require();
        RecognitionMethod method =
                req.method() == null ? RecognitionMethod.STRAIGHT_LINE : req.method();
        int periods = req.periods() == null ? 1 : req.periods();
        LocalDate start = req.start() == null ? LocalDate.now() : req.start();
        RevRecService.ScheduleWithEntries created =
                revRec.createSchedule(
                        merchantId, req.sourceType(), req.sourceRef(), req.totalMinor(),
                        req.currency(), method, start, periods);
        return ResponseEntity.ok(ScheduleView.of(created));
    }

    @GetMapping
    public List<ScheduleSummary> list() {
        return revRec.listSchedules(MerchantContext.require()).stream().map(ScheduleSummary::of).toList();
    }

    @GetMapping("/{scheduleId}")
    public ScheduleView get(@PathVariable UUID scheduleId) {
        return ScheduleView.of(revRec.getSchedule(MerchantContext.require(), scheduleId));
    }

    /** Recognise all slices of this schedule that are due on or before {@code asOf} (default: today). */
    @PostMapping("/{scheduleId}/recognize")
    public ScheduleView recognize(
            @PathVariable UUID scheduleId,
            @RequestParam(required = false) LocalDate asOf) {
        UUID merchantId = MerchantContext.require();
        revRec.recognizeDue(merchantId, scheduleId, asOf == null ? LocalDate.now() : asOf);
        return ScheduleView.of(revRec.getSchedule(merchantId, scheduleId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CreateScheduleRequest(
            @NotBlank String sourceType,
            String sourceRef,
            @NotNull @Positive Long totalMinor,
            @NotBlank String currency,
            RecognitionMethod method,
            LocalDate start,
            @Positive Integer periods) {}

    public record ScheduleSummary(
            String id, String sourceType, String sourceRef, String currency,
            long totalMinor, long recognizedMinor, String method, String status,
            LocalDate periodStart, LocalDate periodEnd, Instant createdAt) {
        static ScheduleSummary of(RecognitionSchedule s) {
            return new ScheduleSummary(
                    s.getId().toString(), s.getSourceType(), s.getSourceRef(), s.getCurrency(),
                    s.getTotalMinor(), s.getRecognizedMinor(), s.getMethod().name(), s.getStatus().name(),
                    s.getPeriodStart(), s.getPeriodEnd(), s.getCreatedAt());
        }
    }

    public record EntryView(
            String id, int seq, LocalDate periodStart, LocalDate periodEnd,
            long amountMinor, String status, String ledgerEntryId, Instant recognizedAt) {
        static EntryView of(RecognitionEntry e) {
            return new EntryView(
                    e.getId().toString(), e.getSeq(), e.getPeriodStart(), e.getPeriodEnd(),
                    e.getAmountMinor(), e.getStatus().name(),
                    e.getLedgerEntryId() == null ? null : e.getLedgerEntryId().toString(),
                    e.getRecognizedAt());
        }
    }

    public record ScheduleView(ScheduleSummary schedule, String deferralEntryId, List<EntryView> entries) {
        static ScheduleView of(RevRecService.ScheduleWithEntries s) {
            return new ScheduleView(
                    ScheduleSummary.of(s.schedule()),
                    s.schedule().getDeferralEntryId().toString(),
                    s.entries().stream().map(EntryView::of).toList());
        }
    }
}
