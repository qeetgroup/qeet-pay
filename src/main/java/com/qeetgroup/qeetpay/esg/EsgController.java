package com.qeetgroup.qeetpay.esg;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ESG / carbon-footprint API (PRD Module 16): record a payment's estimated footprint, list the
 * footprint history, read the aggregate summary, and purchase carbon offsets.
 */
@Tag(
        name = "ESG / Carbon",
        description = "Per-transaction carbon-footprint estimation, footprint history and aggregate summary, and carbon-offset purchases.")
@RestController
@RequestMapping("/v1/esg")
public class EsgController {

    private final EsgService esg;

    public EsgController(EsgService esg) {
        this.esg = esg;
    }

    @PostMapping("/footprints")
    public ResponseEntity<RecordView> record(@Valid @RequestBody FootprintRequest req) {
        CarbonRecord record =
                esg.recordFootprint(
                        MerchantContext.require(), req.transactionRef(), req.method(), req.amountMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(RecordView.of(record));
    }

    @GetMapping("/footprints")
    public List<RecordView> list() {
        return esg.listRecords(MerchantContext.require()).stream().map(RecordView::of).toList();
    }

    @GetMapping("/summary")
    public EsgService.FootprintSummary summary() {
        return esg.footprintSummary(MerchantContext.require());
    }

    @PostMapping("/offsets")
    public ResponseEntity<OffsetView> offset(@Valid @RequestBody OffsetRequest req) {
        CarbonOffset offset =
                esg.purchaseOffset(
                        MerchantContext.require(), req.gramsToOffset(), req.currency(), req.pricePerTonneMinor());
        return ResponseEntity.status(HttpStatus.CREATED).body(OffsetView.of(offset));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record FootprintRequest(
            @NotBlank String transactionRef,
            @NotNull CarbonMethod method,
            @NotNull @PositiveOrZero Long amountMinor) {}

    public record OffsetRequest(
            @NotNull @Positive Long gramsToOffset,
            @NotBlank String currency,
            @NotNull @PositiveOrZero Long pricePerTonneMinor) {}

    public record RecordView(
            String id, String transactionRef, String method, long amountMinor, long gramsCo2,
            Instant createdAt) {
        static RecordView of(CarbonRecord r) {
            return new RecordView(
                    r.getId().toString(), r.getTransactionRef(), r.getMethod().name(),
                    r.getAmountMinor(), r.getGramsCo2(), r.getCreatedAt());
        }
    }

    public record OffsetView(
            String id, long gramsCo2Offset, long costMinor, String currency, String ledgerEntryId,
            String note, Instant createdAt) {
        static OffsetView of(CarbonOffset o) {
            return new OffsetView(
                    o.getId().toString(), o.getGramsCo2Offset(), o.getCostMinor(), o.getCurrency(),
                    o.getLedgerEntryId() == null ? null : o.getLedgerEntryId().toString(),
                    o.getNote(), o.getCreatedAt());
        }
    }
}
