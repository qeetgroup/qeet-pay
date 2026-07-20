package com.qeetgroup.qeetpay.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing API (TAD Module 03): plans, subscriptions, invoices, usage metering.
 * Lifecycle endpoints: upgrade/downgrade/pause/resume/cancel.
 */
@Tag(
        name = "Billing",
        description = "Plans, subscriptions, invoices and usage metering, with lifecycle actions (upgrade/downgrade/pause/resume/cancel).")
@RestController
public class BillingController {

    private final BillingService billing;
    private final UsageMeterService usageMeter;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public BillingController(
            BillingService billing,
            UsageMeterService usageMeter,
            IdempotencyService idempotency,
            ObjectMapper objectMapper) {
        this.billing = billing;
        this.usageMeter = usageMeter;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    // ── Plans ────────────────────────────────────────────────────────────────

    @GetMapping("/v1/plans")
    public List<PlanView> listPlans() {
        return billing.listPlans(MerchantContext.require()).stream().map(PlanView::of).toList();
    }

    @PostMapping("/v1/plans")
    public ResponseEntity<PlanView> createPlan(@Valid @RequestBody CreatePlanRequest req) {
        UUID merchantId = MerchantContext.require();
        Plan plan;
        if (req.pricingModel() != null && req.pricingModel() != PricingModel.FLAT) {
            plan = billing.createPlan(merchantId, req.code(), req.name(), req.amountMinor(),
                    req.currency(), req.interval(), req.pricingModel(),
                    req.tiers(), req.usageMetricKey(), req.trialDays() != null ? req.trialDays() : 0);
        } else {
            plan = billing.createPlan(merchantId, req.code(), req.name(), req.amountMinor(), req.currency(), req.interval());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(PlanView.of(plan));
    }

    // ── Subscriptions ─────────────────────────────────────────────────────────

    @GetMapping("/v1/subscriptions")
    public List<SubscriptionView> listSubscriptions() {
        return billing.listSubscriptions(MerchantContext.require()).stream()
                .map(SubscriptionView::ofSubscription)
                .toList();
    }

    @PostMapping("/v1/subscriptions")
    public ResponseEntity<SubscriptionView> createSubscription(@Valid @RequestBody CreateSubscriptionRequest req) {
        BillingService.Subscribed s = billing.createSubscription(MerchantContext.require(), req.planId(), req.customerRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionView.of(s));
    }

    @PostMapping("/v1/subscriptions/{id}/upgrade")
    public ResponseEntity<UpgradeView> upgradeSubscription(@PathVariable UUID id, @Valid @RequestBody UpgradePlanRequest req) {
        BillingService.ProrationResult result = billing.upgradeSubscription(MerchantContext.require(), id, req.newPlanId());
        return ResponseEntity.ok(new UpgradeView(
                result.subscription().getId().toString(),
                result.subscription().getPlanId().toString(),
                result.prorationMinor(),
                result.prorationInvoice() != null ? result.prorationInvoice().getId().toString() : null));
    }

    @PostMapping("/v1/subscriptions/{id}/downgrade")
    public ResponseEntity<SubscriptionView> downgradeSubscription(@PathVariable UUID id, @Valid @RequestBody UpgradePlanRequest req) {
        Subscription sub = billing.downgradeSubscription(MerchantContext.require(), id, req.newPlanId());
        return ResponseEntity.ok(SubscriptionView.ofSubscription(sub));
    }

    @PostMapping("/v1/subscriptions/{id}/pause")
    public ResponseEntity<SubscriptionView> pauseSubscription(@PathVariable UUID id) {
        return ResponseEntity.ok(SubscriptionView.ofSubscription(billing.pauseSubscription(MerchantContext.require(), id)));
    }

    @PostMapping("/v1/subscriptions/{id}/resume")
    public ResponseEntity<SubscriptionView> resumeSubscription(@PathVariable UUID id) {
        return ResponseEntity.ok(SubscriptionView.ofSubscription(billing.resumeSubscription(MerchantContext.require(), id)));
    }

    @PostMapping("/v1/subscriptions/{id}/cancel")
    public ResponseEntity<SubscriptionView> cancelSubscription(
            @PathVariable UUID id,
            @RequestParam(value = "atPeriodEnd", defaultValue = "false") boolean atPeriodEnd) {
        return ResponseEntity.ok(SubscriptionView.ofSubscription(billing.cancelSubscription(MerchantContext.require(), id, atPeriodEnd)));
    }

    @GetMapping("/v1/subscriptions/{id}")
    public SubscriptionView getSubscription(@PathVariable UUID id) {
        return SubscriptionView.ofSubscription(billing.getSubscription(MerchantContext.require(), id));
    }

    // ── Invoices ──────────────────────────────────────────────────────────────

    @GetMapping("/v1/invoices")
    public List<InvoiceView> listInvoices() {
        return billing.listInvoices(MerchantContext.require()).stream().map(InvoiceView::of).toList();
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
            idempotency.save(merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping("/v1/invoices/{id}")
    public InvoiceView getInvoice(@PathVariable UUID id) {
        return InvoiceView.of(billing.getInvoice(MerchantContext.require(), id));
    }

    // ── Usage Metering ────────────────────────────────────────────────────────

    @PostMapping("/v1/billing/usage")
    public ResponseEntity<Void> ingestUsage(
            @Valid @RequestBody UsageRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        usageMeter.ingest(MerchantContext.require(), req.subscriptionId(), req.metricKey(), req.quantity(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/v1/billing/usage/{subscriptionId}")
    public UsageSummary getUsage(
            @PathVariable UUID subscriptionId,
            @RequestParam String metricKey,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        UUID merchantId = MerchantContext.require();
        long total = usageMeter.aggregateQuantity(merchantId, subscriptionId, metricKey, from, to);
        return new UsageSummary(subscriptionId.toString(), metricKey, total, from.toString(), to.toString());
    }

    // ── Request / view records ────────────────────────────────────────────────

    public record CreatePlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            @Positive long amountMinor,
            @NotBlank String currency,
            @NotNull BillingInterval interval,
            PricingModel pricingModel,
            String tiers,
            String usageMetricKey,
            Integer trialDays) {}

    public record CreateSubscriptionRequest(@NotNull UUID planId, @NotBlank String customerRef) {}

    public record UpgradePlanRequest(@NotNull UUID newPlanId) {}

    public record UsageRequest(@NotNull UUID subscriptionId, @NotBlank String metricKey, @Positive long quantity) {}

    public record PlanView(String id, String code, long amountMinor, String currency, String interval, String pricingModel, int trialDays) {
        static PlanView of(Plan p) {
            return new PlanView(p.getId().toString(), p.getCode(), p.getAmountMinor(), p.getCurrency(),
                    p.getInterval().name(), p.getPricingModel().name(), p.getTrialDays());
        }
    }

    public record SubscriptionView(String id, String planId, String status, String firstInvoiceId, boolean cancelAtPeriodEnd) {
        static SubscriptionView of(BillingService.Subscribed s) {
            return new SubscriptionView(
                    s.subscription().getId().toString(),
                    s.subscription().getPlanId().toString(),
                    s.subscription().getStatus().name(),
                    s.firstInvoice().getId().toString(),
                    s.subscription().isCancelAtPeriodEnd());
        }

        static SubscriptionView ofSubscription(Subscription s) {
            return new SubscriptionView(
                    s.getId().toString(), s.getPlanId().toString(), s.getStatus().name(), null, s.isCancelAtPeriodEnd());
        }
    }

    public record UpgradeView(String subscriptionId, String newPlanId, long prorationMinor, String prorationInvoiceId) {}

    @Schema(name = "BillingInvoiceView")
    public record InvoiceView(String id, String subscriptionId, long amountMinor, String currency, String status, String ledgerEntryId) {
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

    public record UsageSummary(String subscriptionId, String metricKey, long totalQuantity, String from, String to) {}
}
