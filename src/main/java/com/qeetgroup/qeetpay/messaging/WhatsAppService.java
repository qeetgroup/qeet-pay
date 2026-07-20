package com.qeetgroup.qeetpay.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.billing.BillingService;
import com.qeetgroup.qeetpay.billing.Invoice;
import com.qeetgroup.qeetpay.billing.Plan;
import com.qeetgroup.qeetpay.billing.Subscription;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp-native collection + subscription bot (PRD Module 09.2 "WhatsApp Pay" and 09.3 "WhatsApp
 * subscription bot"). Three responsibilities, all merchant-scoped via RLS and outbox-published:
 *
 * <ul>
 *   <li><b>Inbound handling</b> — persists an {@link InboundMessage} idempotently on the provider
 *       message id, so a replayed Meta/WhatsApp webhook is stored (and answered) exactly once.
 *   <li><b>Command bot</b> — parses PAUSE / CANCEL / INVOICE / PLAN / USAGE / BALANCE / PAY from the
 *       inbound text, renders a reply with {@link TemplateRenderer}, and dispatches it via the same
 *       outbox → qeet-notify path the rest of the module uses ({@code notify.dispatch.requested}). For
 *       subscription-affecting commands it <em>reads</em> the billing module (never mutates it) and
 *       emits an <em>intent</em> event ({@code messaging.subscription.*.requested}) — the actual state
 *       change / money move is performed downstream, not here. A {@link WhatsAppSession} tracks state.
 *   <li><b>WhatsApp Pay</b> — records a {@link WhatsAppPayCollection} (CREATED); the confirm callback
 *       posts the canonical money-in ledger entry (debit {@code settlement} / credit {@code revenue})
 *       over a sandbox/simulated rail and marks it PAID (or FAILED).
 * </ul>
 */
@Service
public class WhatsAppService {

    private final InboundMessageRepository inbound;
    private final WhatsAppSessionRepository sessions;
    private final WhatsAppPayCollectionRepository payCollections;
    private final MessageDispatchRepository dispatches;
    private final LedgerService ledger;
    private final BillingService billing;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public WhatsAppService(
            InboundMessageRepository inbound,
            WhatsAppSessionRepository sessions,
            WhatsAppPayCollectionRepository payCollections,
            MessageDispatchRepository dispatches,
            LedgerService ledger,
            BillingService billing,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.inbound = inbound;
        this.sessions = sessions;
        this.payCollections = payCollections;
        this.dispatches = dispatches;
        this.ledger = ledger;
        this.billing = billing;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    // Default bot reply templates (rendered via TemplateRenderer with {{placeholder}} variables).
    private static final String REPLY_PAUSE =
            "We've received your request to pause your subscription (ref {{ref}}). You'll get a confirmation shortly.";
    private static final String REPLY_CANCEL =
            "We've received your request to cancel your subscription (ref {{ref}}). You'll get a confirmation shortly.";
    private static final String REPLY_INVOICE =
            "Your latest invoice is {{amount}} ({{status}}). Reply PAY to settle it.";
    private static final String REPLY_NO_INVOICE = "You have no invoices on record yet.";
    private static final String REPLY_PLAN = "You're on the {{plan}} plan — {{amount}} per {{interval}}.";
    private static final String REPLY_NO_PLAN = "We couldn't find an active subscription for your number.";
    private static final String REPLY_USAGE =
            "Your subscription status is {{status}}. A detailed usage summary will follow shortly.";
    private static final String REPLY_BALANCE = "Your current settlement balance is {{balance}}.";
    private static final String REPLY_PAY =
            "Sure — reply with the amount to pay, or use the payment link we'll send to {{phone}}.";
    private static final String REPLY_HELP =
            "Sorry, I didn't catch that. Try: PAUSE, CANCEL, INVOICE, PLAN, USAGE, BALANCE or PAY.";

    // ── Inbound handling + command bot (09.3) ────────────────────────────────

    /**
     * Handles one inbound WhatsApp message: parses the command, persists the {@link InboundMessage}
     * (idempotent on {@code providerMessageId}), dispatches a templated reply, and advances the
     * conversational {@link WhatsAppSession}. A replayed provider id short-circuits: the stored
     * message is returned without a second reply.
     */
    @Transactional
    public InboundResult handleInbound(UUID merchantId, String from, String text, String providerMessageId) {
        merchantScope.apply(merchantId);
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("from is required");
        }
        if (providerMessageId == null || providerMessageId.isBlank()) {
            throw new IllegalArgumentException("providerMessageId is required");
        }

        var existing = inbound.findByMerchantIdAndProviderMessageId(merchantId, providerMessageId);
        if (existing.isPresent()) {
            return new InboundResult(existing.get(), null, false); // idempotent replay
        }

        BotCommand command = BotCommandParser.parse(text);
        InboundMessage message =
                inbound.save(new InboundMessage(merchantId, providerMessageId, from, text, command.name()));

        BotReply reply = handleCommand(merchantId, from, command);
        MessageDispatch dispatch = dispatchReply(merchantId, from, command, reply, message.getId().toString());
        upsertSession(merchantId, from, command, reply.sessionState(), reply.contextRef());

        return new InboundResult(message, dispatch, true);
    }

    @Transactional(readOnly = true)
    public List<InboundMessage> listInbound(UUID merchantId) {
        merchantScope.apply(merchantId);
        return inbound.findByMerchantIdOrderByReceivedAtDesc(merchantId);
    }

    private BotReply handleCommand(UUID merchantId, String from, BotCommand command) {
        return switch (command) {
            case PAUSE -> subscriptionIntent(merchantId, from, command,
                    "messaging.subscription.pause.requested", REPLY_PAUSE);
            case CANCEL -> subscriptionIntent(merchantId, from, command,
                    "messaging.subscription.cancel.requested", REPLY_CANCEL);
            case INVOICE -> invoiceReply(merchantId, from);
            case PLAN -> planReply(merchantId, from);
            case USAGE -> usageReply(merchantId, from);
            case BALANCE -> balanceReply(merchantId);
            case PAY -> payIntent(merchantId, from);
            case UNKNOWN -> new BotReply(
                    TemplateRenderer.render(REPLY_HELP, Map.of()), WhatsAppSessionState.ACTIVE, null);
        };
    }

    /**
     * Subscription-affecting command: reads billing (never mutates it) to resolve the customer's
     * subscription, emits an outbox <em>intent</em> event, and returns an acknowledgement reply. No
     * money moves and no billing state change happen here — a downstream consumer of the intent does.
     */
    private BotReply subscriptionIntent(
            UUID merchantId, String from, BotCommand command, String eventType, String template) {
        Subscription sub = latestSubscriptionFor(merchantId, from);
        String ref = sub != null ? sub.getId().toString() : from;
        outbox.enqueue(merchantId, eventType, intentJson(from, command, ref));
        return new BotReply(
                TemplateRenderer.render(template, Map.of("ref", ref)), WhatsAppSessionState.ACTIVE, ref);
    }

    private BotReply invoiceReply(UUID merchantId, String from) {
        Subscription sub = latestSubscriptionFor(merchantId, from);
        Invoice inv = sub == null
                ? null
                : billing.listInvoices(merchantId).stream()
                        .filter(i -> i.getSubscriptionId().equals(sub.getId()))
                        .findFirst()
                        .orElse(null);
        if (inv == null) {
            return new BotReply(
                    TemplateRenderer.render(REPLY_NO_INVOICE, Map.of()), WhatsAppSessionState.ACTIVE, null);
        }
        String body = TemplateRenderer.render(
                REPLY_INVOICE,
                Map.of(
                        "amount", formatMoney(inv.getAmountMinor(), inv.getCurrency()),
                        "status", inv.getStatus().name()));
        return new BotReply(body, WhatsAppSessionState.ACTIVE, inv.getId().toString());
    }

    private BotReply planReply(UUID merchantId, String from) {
        Subscription sub = latestSubscriptionFor(merchantId, from);
        Plan plan = sub == null
                ? null
                : billing.listPlans(merchantId).stream()
                        .filter(p -> p.getId().equals(sub.getPlanId()))
                        .findFirst()
                        .orElse(null);
        if (plan == null) {
            return new BotReply(
                    TemplateRenderer.render(REPLY_NO_PLAN, Map.of()), WhatsAppSessionState.ACTIVE, null);
        }
        String body = TemplateRenderer.render(
                REPLY_PLAN,
                Map.of(
                        "plan", plan.getCode(),
                        "amount", formatMoney(plan.getAmountMinor(), plan.getCurrency()),
                        "interval", plan.getInterval().name().toLowerCase(Locale.ROOT)));
        return new BotReply(body, WhatsAppSessionState.ACTIVE, sub.getId().toString());
    }

    private BotReply usageReply(UUID merchantId, String from) {
        Subscription sub = latestSubscriptionFor(merchantId, from);
        String status = sub == null ? "no active subscription" : sub.getStatus().name();
        String body = TemplateRenderer.render(REPLY_USAGE, Map.of("status", status));
        return new BotReply(body, WhatsAppSessionState.ACTIVE, sub == null ? null : sub.getId().toString());
    }

    private BotReply balanceReply(UUID merchantId) {
        long balance = ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "settlement").getId());
        String body = TemplateRenderer.render(REPLY_BALANCE, Map.of("balance", formatMoney(balance, "INR")));
        return new BotReply(body, WhatsAppSessionState.ACTIVE, null);
    }

    private BotReply payIntent(UUID merchantId, String from) {
        outbox.enqueue(merchantId, "messaging.pay.requested", intentJson(from, BotCommand.PAY, from));
        String body = TemplateRenderer.render(REPLY_PAY, Map.of("phone", from));
        return new BotReply(body, WhatsAppSessionState.AWAITING_PAYMENT, from);
    }

    /** Reads billing (newest-first) for the customer's subscription on this phone; null if none. */
    private Subscription latestSubscriptionFor(UUID merchantId, String customerRef) {
        return billing.listSubscriptions(merchantId).stream()
                .filter(s -> customerRef.equals(s.getCustomerRef()))
                .findFirst()
                .orElse(null);
    }

    private MessageDispatch dispatchReply(
            UUID merchantId, String recipient, BotCommand command, BotReply reply, String relatedRef) {
        MessageDispatch dispatch =
                dispatches.save(
                        new MessageDispatch(
                                merchantId,
                                "whatsapp_bot_" + command.name().toLowerCase(Locale.ROOT),
                                MessageChannel.WHATSAPP,
                                recipient,
                                reply.body(),
                                relatedRef));
        outbox.enqueue(merchantId, "notify.dispatch.requested", dispatchJson(dispatch));
        return dispatch;
    }

    private void upsertSession(
            UUID merchantId, String phone, BotCommand command, WhatsAppSessionState state, String contextRef) {
        WhatsAppSession session =
                sessions
                        .findByMerchantIdAndWaPhone(merchantId, phone)
                        .orElseGet(() -> new WhatsAppSession(merchantId, phone));
        session.recordCommand(command, state, contextRef);
        sessions.save(session);
    }

    // ── WhatsApp Pay collection (09.2) ───────────────────────────────────────

    /** Records an in-chat collection request (CREATED). Requires a payer phone or VPA. */
    @Transactional
    public WhatsAppPayCollection createPayCollection(
            UUID merchantId, String payerPhone, String payerVpa, long amountMinor, String currency,
            String description, String relatedRef) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if ((payerPhone == null || payerPhone.isBlank()) && (payerVpa == null || payerVpa.isBlank())) {
            throw new IllegalArgumentException("payer phone or VPA is required");
        }
        String ccy = (currency == null || currency.isBlank()) ? "INR" : currency;
        WhatsAppPayCollection coll =
                payCollections.save(
                        new WhatsAppPayCollection(
                                merchantId, payerPhone, payerVpa, amountMinor, ccy, description, relatedRef));
        outbox.enqueue(merchantId, "whatsapp.pay.created", payJson(coll, null));
        return coll;
    }

    /**
     * Confirm callback for a collection. On {@code success} it posts the canonical money-in entry
     * (debit {@code settlement} / credit {@code revenue}) over a sandbox rail and marks it PAID;
     * otherwise it marks it FAILED. Idempotent: a repeated successful confirm returns the PAID
     * collection without re-posting to the ledger.
     */
    @Transactional
    public WhatsAppPayCollection confirmPayCollection(
            UUID merchantId, UUID collectionId, String providerRef, boolean success) {
        merchantScope.apply(merchantId);
        WhatsAppPayCollection coll = loadCollection(merchantId, collectionId);
        if (coll.getStatus() == WhatsAppPayStatus.PAID) {
            return coll; // idempotent replay
        }
        if (coll.getStatus() != WhatsAppPayStatus.CREATED) {
            throw new IllegalStateException("cannot confirm collection in status " + coll.getStatus());
        }
        if (!success) {
            coll.markFailed(providerRef);
            outbox.enqueue(merchantId, "whatsapp.pay.failed", payJson(coll, null));
            return coll;
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "whatsapp pay " + collectionId,
                        coll.getCurrency(),
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, coll.getAmountMinor()),
                                new LedgerLineInput(revenue, Direction.CREDIT, coll.getAmountMinor())));

        coll.markPaid(
                entryId, providerRef == null ? "wapay_" + collectionId.toString().substring(0, 8) : providerRef);
        outbox.enqueue(merchantId, "whatsapp.pay.confirmed", payJson(coll, entryId));
        return coll;
    }

    @Transactional(readOnly = true)
    public List<WhatsAppPayCollection> listPayCollections(UUID merchantId) {
        merchantScope.apply(merchantId);
        return payCollections.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public WhatsAppPayCollection getPayCollection(UUID merchantId, UUID collectionId) {
        merchantScope.apply(merchantId);
        return loadCollection(merchantId, collectionId);
    }

    private WhatsAppPayCollection loadCollection(UUID merchantId, UUID collectionId) {
        return payCollections
                .findById(collectionId)
                .filter(c -> c.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new MessagingNotFoundException("no whatsapp pay collection " + collectionId));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String formatMoney(long minor, String currency) {
        BigDecimal rupees = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        String prefix = "INR".equalsIgnoreCase(currency) ? "₹" : currency + " ";
        return prefix + rupees.toPlainString();
    }

    private String intentJson(String from, BotCommand command, String ref) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("from", from);
        b.put("command", command.name());
        b.put("ref", ref);
        return write(b, "messaging intent");
    }

    private String dispatchJson(MessageDispatch d) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("dispatchId", d.getId().toString());
        b.put("channel", d.getChannel().name());
        b.put("recipient", d.getRecipient());
        b.put("templateKey", d.getTemplateKey());
        b.put("body", d.getRenderedBody());
        if (d.getRelatedRef() != null) {
            b.put("relatedRef", d.getRelatedRef());
        }
        return write(b, "messaging dispatch");
    }

    private String payJson(WhatsAppPayCollection c, UUID entryId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("collectionId", c.getId().toString());
        b.put("amountMinor", c.getAmountMinor());
        b.put("currency", c.getCurrency());
        b.put("status", c.getStatus().name());
        if (c.getPayerVpa() != null) {
            b.put("payerVpa", c.getPayerVpa());
        }
        if (c.getPayerPhone() != null) {
            b.put("payerPhone", c.getPayerPhone());
        }
        if (entryId != null) {
            b.put("ledgerEntryId", entryId.toString());
        }
        return write(b, "whatsapp pay");
    }

    private String write(Map<String, Object> body, String what) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise " + what + " event", e);
        }
    }

    // ── Result types ─────────────────────────────────────────────────────────

    /** The stored inbound message, the reply dispatch (null on a replay), and whether it was new. */
    public record InboundResult(InboundMessage message, MessageDispatch reply, boolean processed) {}

    private record BotReply(String body, WhatsAppSessionState sessionState, String contextRef) {}
}
