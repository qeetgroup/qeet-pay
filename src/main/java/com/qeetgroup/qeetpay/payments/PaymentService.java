package com.qeetgroup.qeetpay.payments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.fraud.FraudCheck;
import com.qeetgroup.qeetpay.fraud.FraudClient;
import com.qeetgroup.qeetpay.fraud.FraudDecision;
import com.qeetgroup.qeetpay.fraud.FraudDecisionType;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment acceptance (TAD §4.1). {@code create} authorizes via the provider; {@code capture} drives
 * a balanced double-entry posting (debit settlement / credit revenue) through the ledger and records
 * the resulting entry on the payment. Both steps emit outbox events.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentProvider provider;
    private final FraudClient fraud;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository payments,
            RefundRepository refunds,
            PaymentProvider provider,
            FraudClient fraud,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.payments = payments;
        this.refunds = refunds;
        this.provider = provider;
        this.fraud = fraud;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payment create(
            UUID merchantId,
            long amountMinor,
            String currency,
            PaymentMethod method,
            String description,
            boolean simulateFailure) {
        merchantScope.apply(merchantId);
        Payment payment = payments.save(new Payment(merchantId, amountMinor, currency, method, description));

        // Fraud gate (TAD §8.3 — advisory, allow-all when disabled, fail-open on error).
        FraudDecision fraudDecision =
                fraud.score(new FraudCheck(merchantId, payment.getId(), amountMinor, currency, method.name(), null));
        if (fraudDecision.decision() == FraudDecisionType.BLOCK) {
            payment.markFailed("fraud_blocked");
            outbox.enqueue(merchantId, "payment.blocked", json(payment, null));
            return payment;
        }

        PaymentProvider.ProviderResult result = provider.authorize(payment, simulateFailure);
        if (result.success()) {
            payment.markAuthorized(result.providerPaymentId());
            outbox.enqueue(merchantId, "payment.authorized", json(payment, null));
        } else {
            payment.markFailed(result.failureReason());
            outbox.enqueue(merchantId, "payment.failed", json(payment, null));
        }
        return payment;
    }

    @Transactional
    public Payment capture(UUID merchantId, UUID paymentId) {
        merchantScope.apply(merchantId);
        Payment payment = load(merchantId, paymentId);

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return payment; // idempotent: already captured, never double-post
        }
        if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("cannot capture payment in status " + payment.getStatus());
        }

        PaymentProvider.ProviderResult result = provider.capture(payment);
        if (!result.success()) {
            payment.markFailed(result.failureReason());
            throw new IllegalStateException("capture failed: " + result.failureReason());
        }

        UUID entryId = postCaptureEntry(merchantId, payment);
        payment.markCaptured(entryId);
        outbox.enqueue(merchantId, "payment.captured", json(payment, entryId));
        return payment;
    }

    /**
     * Captures a payment in response to a verified provider webhook (Razorpay
     * {@code payment.captured}/{@code payment.authorized}). Unlike {@link #capture}, the money has
     * <em>already</em> moved at the gateway, so this does NOT re-call the provider — it posts the same
     * balanced double-entry (debit settlement / credit revenue) and marks the payment CAPTURED.
     *
     * <p>Idempotent: a redelivered event finds the payment already CAPTURED and returns without a
     * second ledger posting. Called by {@link RazorpayWebhookService} after it has resolved the
     * merchant from the event and is package-private so only the module drives it.
     */
    @Transactional
    Payment captureFromWebhook(UUID merchantId, UUID paymentId, String realProviderPaymentId) {
        merchantScope.apply(merchantId);
        Payment payment = load(merchantId, paymentId);

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return payment; // idempotent: already captured, never double-post
        }
        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "cannot capture payment in status " + payment.getStatus());
        }
        if (realProviderPaymentId != null && !realProviderPaymentId.isBlank()) {
            payment.recordProviderPaymentId(realProviderPaymentId);
        }

        UUID entryId = postCaptureEntry(merchantId, payment);
        payment.markCaptured(entryId);
        outbox.enqueue(merchantId, "payment.captured", json(payment, entryId));
        return payment;
    }

    /**
     * Marks a payment FAILED in response to a verified provider webhook (Razorpay
     * {@code payment.failed}). Idempotent; a captured payment cannot retro-fail, so the event is
     * ignored (returned unchanged) rather than throwing.
     */
    @Transactional
    Payment markFailedFromWebhook(UUID merchantId, UUID paymentId, String reason) {
        merchantScope.apply(merchantId);
        Payment payment = load(merchantId, paymentId);
        if (payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.CAPTURED) {
            return payment; // idempotent / no retro-fail of a captured payment
        }
        payment.markFailed(reason);
        outbox.enqueue(merchantId, "payment.failed", json(payment, null));
        return payment;
    }

    /**
     * Finds a merchant's payment by the provider reference stored on it (the Razorpay {@code order_id}
     * recorded at authorize time). Used by the inbound webhook path to correlate an event that carries
     * only the provider's own identifiers.
     */
    @Transactional(readOnly = true)
    Optional<Payment> findByProviderRef(UUID merchantId, String providerRef) {
        merchantScope.apply(merchantId);
        if (providerRef == null || providerRef.isBlank()) {
            return Optional.empty();
        }
        return payments.findByMerchantIdAndProviderPaymentId(merchantId, providerRef);
    }

    /**
     * Reflects a verified provider refund webhook (Razorpay {@code refund.processed}/
     * {@code refund.created}). Our refunds are created terminal and are append-only (INSERT-only for
     * the app role, V7), so this deliberately does NOT mutate the stored row — it correlates the
     * provider refund id to our record and emits a confirmation outbox event. A refund we have no
     * record of (e.g. initiated out-of-band in the Razorpay dashboard) is not found and is left to
     * settlement reconciliation. Returns {@code true} when a matching refund was found.
     */
    @Transactional
    boolean recordRefundWebhook(UUID merchantId, String providerRefundId, String providerStatus) {
        merchantScope.apply(merchantId);
        Optional<Refund> match =
                (providerRefundId == null || providerRefundId.isBlank())
                        ? Optional.empty()
                        : refunds.findByMerchantIdAndProviderRefundId(merchantId, providerRefundId);
        if (match.isEmpty()) {
            return false;
        }
        outbox.enqueue(
                merchantId,
                "payment.refund." + providerStatus,
                refundWebhookJson(match.get(), providerStatus));
        return true;
    }

    private UUID postCaptureEntry(UUID merchantId, Payment payment) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        return ledger.postEntry(
                merchantId,
                "payment capture " + payment.getId(),
                payment.getCurrency(),
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, payment.getAmountMinor()),
                        new LedgerLineInput(revenue, Direction.CREDIT, payment.getAmountMinor())));
    }

    /**
     * Refunds (fully or partially) a captured payment, reversing the capture posting (debit revenue
     * / credit settlement). Rejects amounts beyond the un-refunded balance.
     */
    @Transactional
    public Refund refund(UUID merchantId, UUID paymentId, long amountMinor, String reason) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("refund amount must be positive");
        }
        Payment payment = load(merchantId, paymentId);
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new IllegalStateException(
                    "can only refund a captured payment; status is " + payment.getStatus());
        }
        long alreadyRefunded =
                refunds.findByPaymentId(paymentId).stream()
                        .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
                        .mapToLong(Refund::getAmountMinor)
                        .sum();
        if (alreadyRefunded + amountMinor > payment.getAmountMinor()) {
            throw new IllegalArgumentException(
                    "refund exceeds refundable amount (" + (payment.getAmountMinor() - alreadyRefunded) + " left)");
        }

        PaymentProvider.ProviderResult result = provider.refund(payment, amountMinor);
        if (!result.success()) {
            throw new IllegalStateException("refund failed: " + result.failureReason());
        }

        // reverse the capture: debit revenue, credit settlement
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "refund " + paymentId,
                        payment.getCurrency(),
                        List.of(
                                new LedgerLineInput(revenue, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(settlement, Direction.CREDIT, amountMinor)));

        Refund refund =
                refunds.save(
                        new Refund(
                                merchantId, paymentId, amountMinor, payment.getCurrency(),
                                RefundStatus.SUCCEEDED, result.providerPaymentId(), reason, entryId));
        outbox.enqueue(merchantId, "payment.refunded", refundJson(refund));
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Refund> refundsOf(UUID merchantId, UUID paymentId) {
        merchantScope.apply(merchantId);
        load(merchantId, paymentId); // ensures the payment belongs to the merchant
        return refunds.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID merchantId, UUID paymentId) {
        merchantScope.apply(merchantId);
        return load(merchantId, paymentId);
    }

    /** The merchant's payments, newest-created first. */
    @Transactional(readOnly = true)
    public List<Payment> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return payments.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Looks up a merchant's payment without throwing — used by cross-module readers (e.g.
     * reconciliation) that treat a missing payment as a domain outcome, not an error.
     */
    @Transactional(readOnly = true)
    public Optional<Payment> find(UUID merchantId, UUID paymentId) {
        merchantScope.apply(merchantId);
        if (paymentId == null) {
            return Optional.empty();
        }
        return payments.findById(paymentId).filter(p -> p.getMerchantId().equals(merchantId));
    }

    private Payment load(UUID merchantId, UUID paymentId) {
        return payments
                .findById(paymentId)
                .filter(p -> p.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PaymentNotFoundException("no payment " + paymentId));
    }

    private String json(Payment payment, UUID entryId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("paymentId", payment.getId().toString());
            body.put("amountMinor", payment.getAmountMinor());
            body.put("status", payment.getStatus().name());
            if (entryId != null) {
                body.put("ledgerEntryId", entryId.toString());
            }
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise payment event", e);
        }
    }

    private String refundJson(Refund refund) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("refundId", refund.getId().toString());
            body.put("paymentId", refund.getPaymentId().toString());
            body.put("amountMinor", refund.getAmountMinor());
            body.put("ledgerEntryId", refund.getLedgerEntryId().toString());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise refund event", e);
        }
    }

    private String refundWebhookJson(Refund refund, String providerStatus) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("refundId", refund.getId().toString());
            body.put("paymentId", refund.getPaymentId().toString());
            body.put("amountMinor", refund.getAmountMinor());
            body.put("providerRefundId", refund.getProviderRefundId());
            body.put("providerStatus", providerStatus);
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise refund webhook event", e);
        }
    }
}
