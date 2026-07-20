package com.qeetgroup.qeetpay.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settlements & reconciliation API (TAD §6.2). POST ingests a provider settlement report — atomic:
 * it posts the money movement to the ledger and reconciles the report against captured payments,
 * returning the settlement with its reconciliation outcome (MATCHED or the flagged discrepancies).
 * Ingestion is idempotent via the provider settlement id, and also honours an {@code Idempotency-Key}.
 */
@Tag(
        name = "Reconciliation",
        description = "Ingest provider settlement reports — post the money movement and auto-reconcile against captured payments.")
@RestController
@RequestMapping("/v1/settlements")
public class SettlementController {

    private final SettlementService settlements;
    private final ReconciliationService reconciliation;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public SettlementController(
            SettlementService settlements,
            ReconciliationService reconciliation,
            IdempotencyService idempotency,
            ObjectMapper objectMapper) {
        this.settlements = settlements;
        this.reconciliation = reconciliation;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> ingest(
            @Valid @RequestBody IngestSettlementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws JsonProcessingException {
        UUID merchantId = MerchantContext.require();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> prior = idempotency.lookup(merchantId, idempotencyKey);
            if (prior.isPresent()) {
                return ResponseEntity.status(prior.get().getResponseStatus())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(prior.get().getResponseBody());
            }
        }

        SettlementReport report =
                new SettlementReport(
                        request.provider(),
                        request.providerSettlementId(),
                        request.currency(),
                        request.settledAt(),
                        request.reportedNetMinor(),
                        request.items().stream()
                                .map(
                                        l ->
                                                new SettlementReport.Line(
                                                        l.paymentId(),
                                                        l.providerPaymentId(),
                                                        l.grossMinor(),
                                                        l.feeMinor(),
                                                        l.taxMinor()))
                                .toList());

        Settlement settlement = settlements.ingest(merchantId, report);
        SettlementView view = view(merchantId, settlement);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId,
                    idempotencyKey,
                    HttpStatus.CREATED.value(),
                    objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public List<SettlementView> list() {
        UUID merchantId = MerchantContext.require();
        return settlements.list(merchantId).stream().map(s -> view(merchantId, s)).toList();
    }

    @GetMapping("/{id}")
    public SettlementView get(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return view(merchantId, settlements.get(merchantId, id));
    }

    private SettlementView view(UUID merchantId, Settlement settlement) {
        ReconciliationView reconciliationView =
                reconciliation
                        .forSettlement(merchantId, settlement.getId())
                        .map(
                                recon ->
                                        new ReconciliationView(
                                                recon.getId().toString(),
                                                recon.getStatus().name(),
                                                recon.getMatchedCount(),
                                                recon.getDiscrepancyCount(),
                                                reconciliation
                                                        .discrepanciesOf(merchantId, recon.getId())
                                                        .stream()
                                                        .map(DiscrepancyView::of)
                                                        .toList()))
                        .orElse(null);
        return SettlementView.of(settlement, reconciliationView);
    }

    public record IngestSettlementRequest(
            @NotBlank String provider,
            @NotBlank String providerSettlementId,
            @NotBlank String currency,
            Instant settledAt,
            Long reportedNetMinor,
            @NotEmpty @Valid List<LineDto> items) {}

    @Schema(name = "SettlementLineDto")
    public record LineDto(
            UUID paymentId,
            String providerPaymentId,
            @Positive long grossMinor,
            @PositiveOrZero long feeMinor,
            @PositiveOrZero long taxMinor) {}

    public record SettlementView(
            String id,
            String provider,
            String providerSettlementId,
            String currency,
            long grossAmountMinor,
            long feeAmountMinor,
            long taxAmountMinor,
            long netAmountMinor,
            int itemCount,
            String status,
            String ledgerEntryId,
            ReconciliationView reconciliation) {

        static SettlementView of(Settlement s, ReconciliationView reconciliation) {
            return new SettlementView(
                    s.getId().toString(),
                    s.getProvider(),
                    s.getProviderSettlementId(),
                    s.getCurrency(),
                    s.getGrossAmountMinor(),
                    s.getFeeAmountMinor(),
                    s.getTaxAmountMinor(),
                    s.getNetAmountMinor(),
                    s.getItemCount(),
                    s.getStatus().name(),
                    s.getLedgerEntryId() == null ? null : s.getLedgerEntryId().toString(),
                    reconciliation);
        }
    }

    public record ReconciliationView(
            String id,
            String status,
            int matchedCount,
            int discrepancyCount,
            List<DiscrepancyView> discrepancies) {}

    public record DiscrepancyView(
            String type,
            String paymentId,
            String providerPaymentId,
            Long expectedMinor,
            Long reportedMinor,
            String detail) {

        static DiscrepancyView of(Discrepancy d) {
            return new DiscrepancyView(
                    d.getType().name(),
                    d.getPaymentId() == null ? null : d.getPaymentId().toString(),
                    d.getProviderPaymentId(),
                    d.getExpectedMinor(),
                    d.getReportedMinor(),
                    d.getDetail());
        }
    }
}
