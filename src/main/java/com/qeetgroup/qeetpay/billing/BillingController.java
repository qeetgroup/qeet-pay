package com.qeetgroup.qeetpay.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing API (TAD Module 03): create plans + subscriptions (which issue the first invoice), and pay
 * an invoice (idempotent) — which posts to the ledger. Active merchant from {@link MerchantContext}.
 */
@RestController
public class BillingController {

    private final BillingService billing;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public BillingController(
            BillingService billing, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.billing = billing;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/plans")
    public ResponseEntity<PlanView> createPlan(@Valid @RequestBody CreatePlanRequest req) {
        Plan plan =
                billing.createPlan(
                        MerchantContext.require(),
                        req.code(),
                        req.name(),
                        req.amountMinor(),
                        req.currency(),
                        req.interval());
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanView.of(plan));
    }

    @PostMapping("/v1/subscriptions")
    public ResponseEntity<SubscriptionView> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest req) {
        BillingService.Subscribed s =
                billing.createSubscription(MerchantContext.require(), req.planId(), req.customerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionView.of(s));
    }

    @PostMapping("/v1/invoices/{id}/pay")
    public ResponseEntity<?> payInvoice(
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

        InvoiceView view = InvoiceView.of(billing.payInvoice(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping("/v1/invoices/{id}")
    public InvoiceView getInvoice(@PathVariable UUID id) {
        return InvoiceView.of(billing.getInvoice(MerchantContext.require(), id));
    }

    @GetMapping("/v1/subscriptions/{id}")
    public SubscriptionView getSubscription(@PathVariable UUID id) {
        return SubscriptionView.ofSubscription(billing.getSubscription(MerchantContext.require(), id));
    }

    public record CreatePlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Positive long amountMinor,
            @NotBlank String currency,
            @NotNull BillingInterval interval) {}

    public record CreateSubscriptionRequest(@NotNull UUID planId, @NotBlank String customerRef) {}

    public record PlanView(String id, String code, long amountMinor, String currency, String interval) {
        static PlanView of(Plan p) {
            return new PlanView(
                    p.getId().toString(), p.getCode(), p.getAmountMinor(), p.getCurrency(), p.getInterval().name());
        }
    }

    public record SubscriptionView(String id, String planId, String status, String firstInvoiceId) {
        static SubscriptionView of(BillingService.Subscribed s) {
            return new SubscriptionView(
                    s.subscription().getId().toString(),
                    s.subscription().getPlanId().toString(),
                    s.subscription().getStatus().name(),
                    s.firstInvoice().getId().toString());
        }

        static SubscriptionView ofSubscription(Subscription s) {
            return new SubscriptionView(
                    s.getId().toString(), s.getPlanId().toString(), s.getStatus().name(), null);
        }
    }

    public record InvoiceView(
            String id, String subscriptionId, long amountMinor, String currency, String status, String ledgerEntryId) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(
                    i.getId().toString(),
                    i.getSubscriptionId().toString(),
                    i.getAmountMinor(),
                    i.getCurrency(),
                    i.getStatus().name(),
                    i.getLedgerEntryId() == null ? null : i.getLedgerEntryId().toString());
        }
    }
}
