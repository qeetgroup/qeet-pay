package com.qeetgroup.qeetpay.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscription billing (TAD Module 03). Creating a subscription activates it and issues the first
 * OPEN invoice for the plan amount; paying an invoice recognises revenue on a cash basis by posting
 * a balanced ledger entry (debit settlement / credit revenue) and emitting an outbox event.
 */
@Service
public class BillingService {

    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final InvoiceRepository invoices;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public BillingService(
            PlanRepository plans,
            SubscriptionRepository subscriptions,
            InvoiceRepository invoices,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.invoices = invoices;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Plan createPlan(
            UUID merchantId, String code, String name, long amountMinor, String currency, BillingInterval interval) {
        merchantScope.apply(merchantId);
        return plans.save(new Plan(merchantId, code, name, amountMinor, currency, interval));
    }

    /** Activates a subscription and issues its first invoice (OPEN) for the plan amount. */
    @Transactional
    public Subscribed createSubscription(UUID merchantId, UUID planId, String customerRef) {
        merchantScope.apply(merchantId);
        Plan plan =
                plans.findById(planId)
                        .filter(p -> p.getMerchantId().equals(merchantId))
                        .orElseThrow(() -> new BillingNotFoundException("no plan " + planId));

        Instant start = Instant.now();
        Instant end = periodEnd(start, plan.getInterval());
        Subscription subscription =
                subscriptions.save(new Subscription(merchantId, planId, customerRef, start, end));

        Invoice invoice =
                invoices.save(
                        new Invoice(merchantId, subscription.getId(), plan.getAmountMinor(), plan.getCurrency()));
        outbox.enqueue(merchantId, "subscription.created", subscriptionJson(subscription, invoice));
        return new Subscribed(subscription, invoice);
    }

    @Transactional
    public Invoice payInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        Invoice invoice = loadInvoice(merchantId, invoiceId);

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return invoice; // idempotent: already paid, never double-post
        }
        if (invoice.getStatus() != InvoiceStatus.OPEN) {
            throw new IllegalStateException("cannot pay invoice in status " + invoice.getStatus());
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
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
        return subscriptions
                .findById(subscriptionId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no subscription " + subscriptionId));
    }

    private Invoice loadInvoice(UUID merchantId, UUID invoiceId) {
        return invoices
                .findById(invoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no invoice " + invoiceId));
    }

    private static Instant periodEnd(Instant start, BillingInterval interval) {
        return switch (interval) {
            case MONTH -> start.atOffset(ZoneOffset.UTC).plusMonths(1).toInstant();
            case YEAR -> start.atOffset(ZoneOffset.UTC).plusYears(1).toInstant();
        };
    }

    /** A newly created subscription plus its first invoice. */
    public record Subscribed(Subscription subscription, Invoice firstInvoice) {}

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

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise billing event", e);
        }
    }
}
