package com.qeetgroup.qeetpay.revrec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revenue recognition (IndAS 115 / ASC 606; TAD §5 "RevRec"). Creating a schedule defers the whole
 * contract amount (debit {@code settlement} / credit {@code deferred_revenue}) and lays out the
 * per-period slices via {@link RevenueScheduler}. As each period falls due, {@link #recognizeDue}
 * moves that slice from deferred revenue into earned revenue (debit {@code deferred_revenue} /
 * credit {@code revenue}) with a balanced ledger posting — idempotent per entry and outbox-published.
 */
@Service
public class RevRecService {

    private final RecognitionScheduleRepository schedules;
    private final RecognitionEntryRepository entries;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public RevRecService(
            RecognitionScheduleRepository schedules,
            RecognitionEntryRepository entries,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.schedules = schedules;
        this.entries = entries;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a recognition schedule for {@code totalMinor} collected upfront, deferring it in the
     * ledger and allocating it across periods per {@code method}.
     */
    @Transactional
    public ScheduleWithEntries createSchedule(
            UUID merchantId,
            String sourceType,
            String sourceRef,
            long totalMinor,
            String currency,
            RecognitionMethod method,
            LocalDate start,
            int periods) {
        merchantScope.apply(merchantId);
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        List<RevenueScheduler.Slice> slices =
                RevenueScheduler.allocate(method, totalMinor, start, periods);
        LocalDate periodEnd = slices.get(slices.size() - 1).periodEnd();

        // Deferral: cash received (settlement) becomes an unearned liability (deferred_revenue).
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID deferred = ledger.accountByCode(merchantId, "deferred_revenue").getId();
        UUID deferralEntry =
                ledger.postEntry(
                        merchantId,
                        "revrec deferral " + sourceType + (sourceRef == null ? "" : "/" + sourceRef),
                        currency,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, totalMinor),
                                new LedgerLineInput(deferred, Direction.CREDIT, totalMinor)));

        RecognitionSchedule schedule =
                schedules.save(
                        new RecognitionSchedule(
                                merchantId, sourceType, sourceRef, currency, totalMinor, method,
                                start, periodEnd, deferralEntry));

        List<RecognitionEntry> saved = new ArrayList<>(slices.size());
        for (RevenueScheduler.Slice s : slices) {
            saved.add(
                    entries.save(
                            new RecognitionEntry(
                                    schedule.getId(), merchantId, s.seq(),
                                    s.periodStart(), s.periodEnd(), s.amountMinor())));
        }

        outbox.enqueue(merchantId, "revrec.schedule.created", scheduleJson(schedule));
        return new ScheduleWithEntries(schedule, saved);
    }

    /**
     * Recognises every PENDING slice of one schedule whose period has ended on or before {@code asOf}.
     * Returns the number of slices recognised (0 if none are due). Idempotent: re-running only picks
     * up newly-due slices.
     */
    @Transactional
    public int recognizeDue(UUID merchantId, UUID scheduleId, LocalDate asOf) {
        merchantScope.apply(merchantId);
        RecognitionSchedule schedule = load(merchantId, scheduleId);
        if (schedule.getStatus() == RecognitionStatus.CANCELLED) {
            throw new IllegalStateException("schedule is cancelled");
        }
        List<RecognitionEntry> due =
                entries.findByScheduleIdAndStatusAndPeriodEndLessThanEqualOrderBySeq(
                        scheduleId, RecognitionEntryStatus.PENDING, asOf);
        for (RecognitionEntry entry : due) {
            recognizeEntry(merchantId, schedule, entry);
        }
        if (!due.isEmpty()) {
            schedules.save(schedule);
        }
        return due.size();
    }

    /** Cross-schedule sweep: recognises all of a merchant's PENDING slices due on or before {@code asOf}. */
    @Transactional
    public int recognizeAllDue(UUID merchantId, LocalDate asOf) {
        merchantScope.apply(merchantId);
        List<RecognitionEntry> due =
                entries.findByMerchantIdAndStatusAndPeriodEndLessThanEqualOrderByPeriodEnd(
                        merchantId, RecognitionEntryStatus.PENDING, asOf);
        Map<UUID, RecognitionSchedule> touched = new LinkedHashMap<>();
        for (RecognitionEntry entry : due) {
            RecognitionSchedule schedule =
                    touched.computeIfAbsent(entry.getScheduleId(), id -> load(merchantId, id));
            if (schedule.getStatus() == RecognitionStatus.CANCELLED) {
                continue;
            }
            recognizeEntry(merchantId, schedule, entry);
        }
        touched.values().forEach(schedules::save);
        return due.size();
    }

    private void recognizeEntry(UUID merchantId, RecognitionSchedule schedule, RecognitionEntry entry) {
        UUID deferred = ledger.accountByCode(merchantId, "deferred_revenue").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID ledgerEntry =
                ledger.postEntry(
                        merchantId,
                        "revrec recognition " + schedule.getSourceType() + " #" + entry.getSeq(),
                        schedule.getCurrency(),
                        List.of(
                                new LedgerLineInput(deferred, Direction.DEBIT, entry.getAmountMinor()),
                                new LedgerLineInput(revenue, Direction.CREDIT, entry.getAmountMinor())));
        entry.markRecognized(ledgerEntry);
        entries.save(entry);
        schedule.applyRecognition(entry.getAmountMinor());
        outbox.enqueue(merchantId, "revrec.revenue.recognized", recognizedJson(schedule, entry, ledgerEntry));
    }

    @Transactional(readOnly = true)
    public ScheduleWithEntries getSchedule(UUID merchantId, UUID scheduleId) {
        merchantScope.apply(merchantId);
        RecognitionSchedule schedule = load(merchantId, scheduleId);
        return new ScheduleWithEntries(schedule, entries.findByScheduleIdOrderBySeq(scheduleId));
    }

    @Transactional(readOnly = true)
    public List<RecognitionSchedule> listSchedules(UUID merchantId) {
        merchantScope.apply(merchantId);
        return schedules.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private RecognitionSchedule load(UUID merchantId, UUID scheduleId) {
        return schedules
                .findById(scheduleId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new RecognitionScheduleNotFoundException("no schedule " + scheduleId));
    }

    /** A schedule plus its per-period entries. */
    public record ScheduleWithEntries(RecognitionSchedule schedule, List<RecognitionEntry> entries) {}

    private String scheduleJson(RecognitionSchedule s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("scheduleId", s.getId().toString());
        b.put("sourceType", s.getSourceType());
        b.put("totalMinor", s.getTotalMinor());
        b.put("deferralEntryId", s.getDeferralEntryId().toString());
        return write(b);
    }

    private String recognizedJson(RecognitionSchedule s, RecognitionEntry e, UUID ledgerEntry) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("scheduleId", s.getId().toString());
        b.put("entryId", e.getId().toString());
        b.put("seq", e.getSeq());
        b.put("amountMinor", e.getAmountMinor());
        b.put("recognizedMinor", s.getRecognizedMinor());
        b.put("ledgerEntryId", ledgerEntry.toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise revrec event", e);
        }
    }
}
