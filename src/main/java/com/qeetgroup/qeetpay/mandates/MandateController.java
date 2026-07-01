package com.qeetgroup.qeetpay.mandates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
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
 * Mandate management REST API (TAD Module 02). Lifecycle: create → activate → (pause/resume) →
 * revoke. debit() posts a charge and is idempotent via {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/v1/mandates")
public class MandateController {

    private final MandateService service;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public MandateController(
            MandateService service, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.service = service;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<MandateView> create(@Valid @RequestBody CreateMandateRequest req) {
        Mandate m = service.create(
                MerchantContext.require(),
                req.customerRef(),
                req.type(),
                req.limitMinor(),
                req.currency() != null ? req.currency() : "INR",
                req.frequency(),
                req.startDate(),
                req.endDate());
        return ResponseEntity.status(HttpStatus.CREATED).body(MandateView.of(m));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<MandateView> activate(
            @PathVariable UUID id,
            @RequestBody(required = false) ActivateRequest req) {
        Mandate m = service.activate(
                MerchantContext.require(), id,
                req != null ? req.providerMandateId() : null);
        return ResponseEntity.ok(MandateView.of(m));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<MandateView> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(MandateView.of(service.pause(MerchantContext.require(), id)));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<MandateView> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(MandateView.of(service.revoke(MerchantContext.require(), id)));
    }

    @PostMapping("/{id}/debit")
    public ResponseEntity<?> debit(
            @PathVariable UUID id,
            @Valid @RequestBody DebitRequest req,
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

        MandateDebitView view = MandateDebitView.of(
                service.debit(merchantId, id, req.amountMinor(), req.description()));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(merchantId, idempotencyKey,
                    HttpStatus.CREATED.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    public List<MandateView> list() {
        return service.list(MerchantContext.require()).stream().map(MandateView::of).toList();
    }

    @GetMapping("/{id}")
    public MandateView get(@PathVariable UUID id) {
        return MandateView.of(service.get(MerchantContext.require(), id));
    }

    @GetMapping("/{id}/debits")
    public List<MandateDebitView> debits(@PathVariable UUID id) {
        return service.debitsOf(MerchantContext.require(), id)
                .stream().map(MandateDebitView::of).toList();
    }

    // ── Request / view records ──────────────────────────────────────────────

    public record CreateMandateRequest(
            @NotBlank String customerRef,
            @NotNull MandateType type,
            @Positive long limitMinor,
            String currency,
            @NotNull MandateFrequency frequency,
            @NotNull LocalDate startDate,
            LocalDate endDate) {}

    public record ActivateRequest(String providerMandateId) {}

    public record DebitRequest(
            @Positive long amountMinor,
            String description) {}

    public record MandateView(
            String id, String customerId, String type, long limitMinor, String currency,
            String frequency, String startDate, String endDate, String status, String providerMandateId) {
        static MandateView of(Mandate m) {
            return new MandateView(
                    m.getId().toString(),
                    m.getCustomerId() != null ? m.getCustomerId().toString() : null,
                    m.getType().name(),
                    m.getLimitMinor(),
                    m.getCurrency(),
                    m.getFrequency().name(),
                    m.getStartDate().toString(),
                    m.getEndDate() != null ? m.getEndDate().toString() : null,
                    m.getStatus().name(),
                    m.getProviderMandateId());
        }
    }

    public record MandateDebitView(
            String id, String mandateId, long amountMinor, String currency,
            String status, String ledgerEntryId) {
        static MandateDebitView of(MandateDebit d) {
            return new MandateDebitView(
                    d.getId().toString(),
                    d.getMandateId().toString(),
                    d.getAmountMinor(),
                    d.getCurrency(),
                    d.getStatus(),
                    d.getLedgerEntryId() != null ? d.getLedgerEntryId().toString() : null);
        }
    }
}
