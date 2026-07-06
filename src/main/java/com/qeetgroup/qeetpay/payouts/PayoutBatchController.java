package com.qeetgroup.qeetpay.payouts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bulk payouts API (TAD §17). Create a batch from a JSON array or a CSV upload (all members staged
 * PENDING_APPROVAL); approve is the maker-checker step that disburses the whole batch (idempotent);
 * reject closes it. The active merchant comes from {@link MerchantContext}.
 */
@RestController
@RequestMapping("/v1/payout-batches")
public class PayoutBatchController {

    private final BulkPayoutService bulk;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PayoutBatchController(
            BulkPayoutService bulk, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.bulk = bulk;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<PayoutBatchView> create(@Valid @RequestBody CreateBatchRequest request) {
        UUID merchantId = MerchantContext.require();
        List<PayoutInstruction> instructions =
                request.payouts().stream()
                        .map(p -> new PayoutInstruction(p.amountMinor(), p.rail(), p.destination(), p.description()))
                        .toList();
        PayoutBatch batch = bulk.createBatch(merchantId, request.currency(), request.description(), instructions);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(merchantId, batch));
    }

    @PostMapping(value = "/csv", consumes = "text/csv")
    public ResponseEntity<PayoutBatchView> createFromCsv(
            @RequestBody String csv,
            @RequestParam(defaultValue = "INR") String currency,
            @RequestParam(required = false) String description) {
        UUID merchantId = MerchantContext.require();
        PayoutBatch batch = bulk.createBatchFromCsv(merchantId, currency, description, csv);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(merchantId, batch));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
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

        PayoutBatchView view = view(merchantId, bulk.approveBatch(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/reject")
    public PayoutBatchView reject(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return view(merchantId, bulk.rejectBatch(merchantId, id));
    }

    @GetMapping("/{id}")
    public PayoutBatchView get(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return view(merchantId, bulk.get(merchantId, id));
    }

    @GetMapping
    public List<PayoutBatchSummaryView> list() {
        return bulk.list(MerchantContext.require()).stream().map(PayoutBatchSummaryView::of).toList();
    }

    private PayoutBatchView view(UUID merchantId, PayoutBatch batch) {
        List<PayoutLineView> lines =
                bulk.payoutsOf(merchantId, batch.getId()).stream().map(PayoutLineView::of).toList();
        return PayoutBatchView.of(batch, lines);
    }

    public record CreateBatchRequest(
            @NotBlank String currency, String description, @NotEmpty @Valid List<PayoutLineDto> payouts) {}

    public record PayoutLineDto(
            @Positive long amountMinor,
            @NotNull PayoutRail rail,
            @NotBlank String destination,
            String description) {}

    public record PayoutBatchView(
            String id,
            String currency,
            String status,
            int totalCount,
            long totalAmountMinor,
            int paidCount,
            int failedCount,
            String description,
            List<PayoutLineView> payouts) {

        static PayoutBatchView of(PayoutBatch b, List<PayoutLineView> payouts) {
            return new PayoutBatchView(
                    b.getId().toString(),
                    b.getCurrency(),
                    b.getStatus().name(),
                    b.getTotalCount(),
                    b.getTotalAmountMinor(),
                    b.getPaidCount(),
                    b.getFailedCount(),
                    b.getDescription(),
                    payouts);
        }
    }

    public record PayoutBatchSummaryView(
            String id,
            String currency,
            String status,
            int totalCount,
            long totalAmountMinor,
            int paidCount,
            int failedCount,
            String description) {

        static PayoutBatchSummaryView of(PayoutBatch b) {
            return new PayoutBatchSummaryView(
                    b.getId().toString(),
                    b.getCurrency(),
                    b.getStatus().name(),
                    b.getTotalCount(),
                    b.getTotalAmountMinor(),
                    b.getPaidCount(),
                    b.getFailedCount(),
                    b.getDescription());
        }
    }

    public record PayoutLineView(
            String id, long amountMinor, String rail, String destination, String status, String ledgerEntryId) {

        static PayoutLineView of(Payout p) {
            return new PayoutLineView(
                    p.getId().toString(),
                    p.getAmountMinor(),
                    p.getRail().name(),
                    p.getDestination(),
                    p.getStatus().name(),
                    p.getLedgerEntryId() == null ? null : p.getLedgerEntryId().toString());
        }
    }
}
