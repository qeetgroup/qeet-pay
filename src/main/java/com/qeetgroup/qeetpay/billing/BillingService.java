package com.qeetgroup.qeetpay.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription billing (TAD Module 03). Supports:
 * <ul>
 *   <li>FLAT, PER_UNIT, TIERED, VOLUME, HYBRID pricing models via {@link PricingCalculator}</li>
 *   <li>Trial periods, upgrade/downgrade with proration, pause/resume, and cancel (immediate or at period end)</li>
 *   <li>All lifecycle changes emit an outbox event and append a {@link SubscriptionEvent}</li>
 * </ul>
 */
@Service
public class BillingService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final SubscriptionEventRepository subEvents;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final PricingCalculator pricing;
    private final ObjectMapper objectMapper;

    public BillingService(
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            InvoiceRepository invoices,
            SubscriptionEventRepository subEvents,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            PricingCalculator pricing,
            ObjectMapper objectMapper) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.subEvents = subEvents;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.pricing = pricing;
        this.objectMapper = objectMapper;
    }

    // ── Plans ───────────────────────────────────────────────────────────────

    @Transactional
    public Plan createPlan(UUID merchantId, String code, String name, long amountMinor, String currency, BillingInterval interval) {
        merchantScope.apply(merchantId);
        return plans.save(new Plan(merchantId, code, name, amountMinor, currency, interval));
    }

    @Transactional
    public Plan createPlan(UUID merchantId, String code, String name, long amountMinor, String currency,
            BillingInterval interval, PricingModel model, String tiersJson, String metricKey, int trialDays) {
        merchantScope.apply(merchantId);
        Plan plan = new Plan(merchantId, code, name, amountMinor, currency, interval)
                .withPricingModel(model, tiersJson, metricKey)
                .withTrialDays(trialDays);
        return plans.save(plan);
    }

    // ── Subscriptions ───────────────────────────────────────────────────────

    /** Activates a subscription and issues its first invoice. Starts a trial if the plan has trial_days > 0. */
    @Transactional
    public Subscribed createSubscription(UUID merchantId, UUID planId, String customerRef) {
        merchantScope.apply(merchantId);
        Plan plan = loadPlan(merchantId, planId);

        Instant start = Instant.now();
        Instant end = periodEnd(start, plan.getInterval());
        Subscription sub = new Subscription(merchantId, planId, customerRef, start, end);

        if (plan.getTrialDays() > 0) {
            sub.startTrial(start.plus(plan.getTrialDays(), ChronoUnit.DAYS));
        }
        subscriptions.save(sub);
        recordEvent(merchantId, sub.getId(), "subscription.created", null);

        // Issue first invoice (amount=0 during trial; full amount for FLAT immediately)
        long invoiceAmount = sub.getStatus() == SubscriptionStatus.TRIALING ? 0L : plan.getAmountMinor();
        Invoice invoice = invoices.save(new Invoice(merchantId, sub.getId(), invoiceAmount > 0 ? invoiceAmount : plan.getAmountMinor(), plan.getCurrency()));
        outbox.enqueue(merchantId, "subscription.created", subscriptionJson(sub, invoice));
        return new Subscribed(sub, invoice);
    }

    /** Upgrade to a new plan immediately with prorated credit/charge for the remainder of the period. */
    @Transactional
    public ProrationResult upgradeSubscription(UUID merchantId, UUID subscriptionId, UUID newPlanId) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        Plan oldPlan = loadPlan(merchantId, sub.getPlanId());
        Plan newPlan = loadPlan(merchantId, newPlanId);

        long prorationMinor = computeProration(sub, oldPlan, newPlan);
        sub.upgradePlan(newPlanId);

        Invoice invoice = null;
        if (prorationMinor > 0) {
            invoice = invoices.save(new Invoice(merchantId, subscriptionId, prorationMinor, newPlan.getCurrency()));
        }
        recordEvent(merchantId, subscriptionId, "subscription.upgraded",
                json("fromPlan", oldPlan.getId(), "toPlan", newPlanId));
        outbox.enqueue(merchantId, "subscription.upgraded",
                json("fromPlan", oldPlan.getId(), "toPlan", newPlanId));
        return new ProrationResult(sub, invoice, prorationMinor);
    }

    /** Downgrade takes effect at the next billing period (no immediate charge). */
    @Transactional
    public Subscription downgradeSubscription(UUID merchantId, UUID subscriptionId, UUID newPlanId) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        loadPlan(merchantId, newPlanId); // validates newPlan belongs to merchant
        sub.upgradePlan(newPlanId);
        recordEvent(merchantId, subscriptionId, "subscription.downgraded",
                json("toPlan", newPlanId, "effectiveAt", "period_end"));
        outbox.enqueue(merchantId, "subscription.downgraded",
                json("toPlan", newPlanId, "subscriptionId", subscriptionId));
        return sub;
    }

    @Transactional
    public Subscription pauseSubscription(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        sub.pause();
        recordEvent(merchantId, subscriptionId, "subscription.paused", null);
        outbox.enqueue(merchantId, "subscription.paused",
                json("subscriptionId", subscriptionId, "toPlan", sub.getPlanId()));
        return sub;
    }

    @Transactional
    public Subscription resumeSubscription(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        sub.resume();
        recordEvent(merchantId, subscriptionId, "subscription.resumed", null);
        outbox.enqueue(merchantId, "subscription.resumed",
                json("subscriptionId", subscriptionId, "toPlan", sub.getPlanId()));
        return sub;
    }

    @Transactional
    public Subscription markPastDue(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        sub.markPastDue();
        recordEvent(merchantId, subscriptionId, "subscription.past_due", null);
        outbox.enqueue(merchantId, "subscription.past_due", write(Map.of("subscriptionId", subscriptionId.toString())));
        return sub;
    }

    @Transactional
    public Subscription cancelSubscription(UUID merchantId, UUID subscriptionId, boolean atPeriodEnd) {
        merchantScope.apply(merchantId);
        Subscription sub = loadSubscription(merchantId, subscriptionId);
        sub.cancel(atPeriodEnd);
        recordEvent(merchantId, subscriptionId, "subscription.cancelled",
                json("atPeriodEnd", Boolean.toString(atPeriodEnd), "subscriptionId", subscriptionId));
        outbox.enqueue(merchantId, "subscription.cancelled",
                json("subscriptionId", subscriptionId, "atPeriodEnd", atPeriodEnd));
        return sub;
    }

    // ── Invoicing ───────────────────────────────────────────────────────────

    @Transactional
    public Invoice payInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        Invoice invoice = loadInvoice(merchantId, invoiceId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice;
        }
        if (invoice.getStatus() != InvoiceStatus.OPEN) {
            throw new IllegalStateException("cannot pay invoice in status " + invoice.getStatus());
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId = ledger.postEntry(
                merchantId,
                "invoice " + invoice.getId(),
                invoice.getCurrency(),
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, invoice.getAmountMinor()),
                        new LedgerLineInput(revenue, Direction.CREDIT, invoice.getAmountMinor())));

        invoice.markPaid(entryId);
        outbox.enqueue(merchantId, "invoice.paid", invoiceJson(invoice, entryId));
        return invoice;
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        return loadInvoice(merchantId, invoiceId);
    }

    @Transactional(readOnly = true)
    public Subscription getSubscription(UUID merchantId, UUID subscriptionId) {
        merchantScope.apply(merchantId);
        return loadSubscription(merchantId, subscriptionId);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Plan loadPlan(UUID merchantId, UUID planId) {
        return plans.findById(planId)
                .filter(p -> p.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no plan " + planId));
    }

    private Subscription loadSubscription(UUID merchantId, UUID subscriptionId) {
        return subscriptions.findById(subscriptionId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no subscription " + subscriptionId));
    }

    private Invoice loadInvoice(UUID merchantId, UUID invoiceId) {
        return invoices.findById(invoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no invoice " + invoiceId));
    }

    /**
     * Proration: pro-rate old plan amount for used days, charge new plan amount for remaining days.
     * All arithmetic uses BigDecimal HALF_UP; result in minor units.
     */
    private long computeProration(Subscription sub, Plan oldPlan, Plan newPlan) {
        Instant now = Instant.now();
        long totalSeconds = sub.getCurrentPeriodEnd().getEpochSecond() - sub.getCurrentPeriodStart().getEpochSecond();
        if (totalSeconds <= 0) return 0L;
        long usedSeconds = now.getEpochSecond() - sub.getCurrentPeriodStart().getEpochSecond();
        long remainSeconds = sub.getCurrentPeriodEnd().getEpochSecond() - now.getEpochSecond();

        BigDecimal total = BigDecimal.valueOf(totalSeconds);
        BigDecimal oldCredit = BigDecimal.valueOf(oldPlan.getAmountMinor())
                .multiply(BigDecimal.valueOf(remainSeconds)).divide(total, 0, RoundingMode.HALF_UP);
        BigDecimal newCharge = BigDecimal.valueOf(newPlan.getAmountMinor())
                .multiply(BigDecimal.valueOf(remainSeconds)).divide(total, 0, RoundingMode.HALF_UP);
        long net = newCharge.subtract(oldCredit).longValue();
        return Math.max(net, 0L);
    }

    private void recordEvent(UUID merchantId, UUID subscriptionId, String type, String metadata) {
        subEvents.save(new SubscriptionEvent(merchantId, subscriptionId, type, metadata));
    }

    private static Instant periodEnd(Instant start, BillingInterval interval) {
        return switch (interval) {
            case MONTH -> start.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
            case YEAR -> start.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
        };
    }

    // ── Event payload helpers ────────────────────────────────────────────────

    private String subscriptionJson(Subscription s, Invoice invoice) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subscriptionId", s.getId().toString());
        body.put("invoiceId", invoice.getId().toString());
        body.put("amountMinor", invoice.getAmountMinor());
        return write(body);
    }

    private String invoiceJson(Invoice invoice, UUID entryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceId", invoice.getId().toString());
        body.put("amountMinor", invoice.getAmountMinor());
        body.put("ledgerEntryId", entryId.toString());
        return write(body);
    }

    private String json(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(k1, v1 instanceof UUID ? v1.toString() : v1);
        body.put(k2, v2 instanceof UUID ? v2.toString() : v2);
        return write(body);
    }

    private String json(String k1, UUID v1, String k2, UUID v2) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(k1, v1.toString());
        body.put(k2, v2.toString());
        return write(body);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise billing event", e);
        }
    }

    // ── Result types ─────────────────────────────────────────────────────────

    public record Subscribed(Subscription subscription, Invoice firstInvoice) {}

    public record ProrationResult(Subscription subscription, Invoice prorationInvoice, long prorationMinor) {}
}
