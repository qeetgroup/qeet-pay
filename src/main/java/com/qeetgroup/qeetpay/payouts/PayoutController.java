package com.qeetgroup.qeetpay.payouts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
 * Payouts API (TAD Module 02). Create stages a payout (PENDING_APPROVAL); approve (idempotent) is
 * the maker-checker step that disburses and posts to the ledger; reject closes it.
 */
@Tag(
        name = "Payouts",
        description = "Create, approve (maker-checker, idempotent) and reject single payouts; approval disburses and posts to the ledger.")
@RestController
@RequestMapping("/v1/payouts")
public class PayoutController {

    private final PayoutService payouts;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PayoutController(
            PayoutService payouts, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.payouts = payouts;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<PayoutView> create(@Valid @RequestBody CreatePayoutRequest request) {
        Payout payout =
                payouts.create(
                        MerchantContext.require(),
                        request.amountMinor(),
                        request.currency(),
                        request.rail(),
                        request.destination(),
                        request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(PayoutView.of(payout));
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

        PayoutView view = PayoutView.of(payouts.approve(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/reject")
    public PayoutView reject(@PathVariable UUID id) {
        return PayoutView.of(payouts.reject(MerchantContext.require(), id));
    }

    @GetMapping("/{id}")
    public PayoutView get(@PathVariable UUID id) {
        return PayoutView.of(payouts.get(MerchantContext.require(), id));
    }

    public record CreatePayoutRequest(
            @Positive long amountMinor,
            @NotBlank String currency,
            @NotNull PayoutRail rail,
            @NotBlank String destination,
            String description) {}

    public record PayoutView(
            String id,
            long amountMinor,
            String currency,
            String rail,
            String status,
            String ledgerEntryId) {
        static PayoutView of(Payout p) {
            return new PayoutView(
                    p.getId().toString(),
                    p.getAmountMinor(),
                    p.getCurrency(),
                    p.getRail().name(),
                    p.getStatus().name(),
                    p.getLedgerEntryId() == null ? null : p.getLedgerEntryId().toString());
        }
    }
}
