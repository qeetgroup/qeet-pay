package com.qeetgroup.qeetpay.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment acceptance API (TAD Module 01). Create authorizes immediately; capture is idempotent
 * (replay via {@code Idempotency-Key}) and posts the ledger entry. The active merchant comes from
 * {@link MerchantContext} (API-key / JWT auth, or the dev {@code X-Merchant-Id} header).
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService payments;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PaymentController(
            PaymentService payments, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<PaymentView> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment =
                payments.create(
                        MerchantContext.require(),
                        request.amountMinor(),
                        request.currency(),
                        request.method(),
                        request.description(),
                        Boolean.TRUE.equals(request.simulateFailure()));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentView.of(payment));
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<?> capture(
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

        PaymentView view = PaymentView.of(payments.capture(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId,
                    idempotencyKey,
                    HttpStatus.OK.value(),
                    objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request,
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

        RefundView view = RefundView.of(payments.refund(merchantId, id, request.amountMinor(), request.reason()));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.CREATED.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping("/{id}/refunds")
    public List<RefundView> refunds(@PathVariable UUID id) {
        return payments.refundsOf(MerchantContext.require(), id).stream().map(RefundView::of).toList();
    }

    @GetMapping("/{id}")
    public PaymentView get(@PathVariable UUID id) {
        return PaymentView.of(payments.get(MerchantContext.require(), id));
    }

    public record CreatePaymentRequest(
            @Positive long amountMinor,
            @NotBlank String currency,
            @NotNull PaymentMethod method,
            String description,
            Boolean simulateFailure) {}

    public record PaymentView(
            String id,
            long amountMinor,
            String currency,
            String method,
            String status,
            String ledgerEntryId) {
        static PaymentView of(Payment p) {
            return new PaymentView(
                    p.getId().toString(),
                    p.getAmountMinor(),
                    p.getCurrency(),
                    p.getMethod().name(),
                    p.getStatus().name(),
                    p.getLedgerEntryId() == null ? null : p.getLedgerEntryId().toString());
        }
    }

    public record RefundRequest(@Positive long amountMinor, String reason) {}

    public record RefundView(
            String id, String paymentId, long amountMinor, String status, String ledgerEntryId) {
        static RefundView of(Refund r) {
            return new RefundView(
                    r.getId().toString(),
                    r.getPaymentId().toString(),
                    r.getAmountMinor(),
                    r.getStatus().name(),
                    r.getLedgerEntryId() == null ? null : r.getLedgerEntryId().toString());
        }
    }
}
