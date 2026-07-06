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

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "payment capture " + payment.getId(),
                        payment.getCurrency(),
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, payment.getAmountMinor()),
                                new LedgerLineInput(revenue, Direction.CREDIT, payment.getAmountMinor())));

        payment.markCaptured(entryId);
        outbox.enqueue(merchantId, "payment.captured", json(payment, entryId));
        return payment;
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
}
