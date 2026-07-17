package com.qeetgroup.qeetpay.paymentlinks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.payments.Payment;
import com.qeetgroup.qeetpay.payments.PaymentMethod;
import com.qeetgroup.qeetpay.payments.PaymentService;
import com.qeetgroup.qeetpay.payments.PaymentStatus;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment links (PRD Module 01). Creating a link mints a shareable code; paying it drives a real
 * payment through the {@code payments} module (create → capture), which posts the money-in ledger
 * entry, then records the payment id on the link. Merchant-scoped via RLS; every write is outbox-published.
 */
@Service
public class PaymentLinkService {

    private final PaymentLinkRepository links;
    private final LinkPublicLookupRepository lookups;
    private final PaymentService payments;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public PaymentLinkService(
            PaymentLinkRepository links,
            LinkPublicLookupRepository lookups,
            PaymentService payments,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.links = links;
        this.lookups = lookups;
        this.payments = payments;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Creates a link. A null {@code amountMinor} means the payer chooses the amount at pay time. */
    @Transactional
    public PaymentLink createLink(
            UUID merchantId, String title, Long amountMinor, String currency,
            String reference, Instant expiresAt) {
        merchantScope.apply(merchantId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (amountMinor != null && amountMinor <= 0) {
            throw new IllegalArgumentException("amountMinor must be positive when set");
        }
        PaymentLink link =
                links.save(new PaymentLink(merchantId, generateCode(), title, amountMinor, currency, reference, expiresAt));
        // Dual-write the public routing map (code → merchant/link) so the hosted-checkout path can
        // resolve this code with no merchant context. The lookup table carries no sensitive data and
        // has no RLS (see V36__checkout_link_lookup.sql).
        lookups.save(new LinkPublicLookup(link.getCode(), merchantId, link.getId()));
        outbox.enqueue(merchantId, "paymentlink.created", linkJson(link));
        return link;
    }

    /**
     * Pays a link by driving a real payment (authorize + capture). For a fixed-amount link the
     * {@code amountMinor} argument is ignored; for an open link it is required.
     */
    @Transactional
    public PaymentLink pay(UUID merchantId, String code, PaymentMethod method, Long amountMinor, boolean simulateFailure) {
        merchantScope.apply(merchantId);
        PaymentLink link = loadByCode(merchantId, code);
        if (link.getStatus() != PaymentLinkStatus.ACTIVE) {
            throw new IllegalStateException("link is " + link.getStatus() + ", cannot be paid");
        }
        if (link.isExpired(Instant.now())) {
            link.markExpired();
            links.save(link);
            throw new IllegalStateException("link has expired");
        }

        long chargeMinor;
        if (link.isFixedAmount()) {
            chargeMinor = link.getAmountMinor();
        } else {
            if (amountMinor == null || amountMinor <= 0) {
                throw new IllegalArgumentException("this link requires a positive amountMinor at pay time");
            }
            chargeMinor = amountMinor;
        }

        Payment payment =
                payments.create(merchantId, chargeMinor, link.getCurrency(), method, "link:" + code, simulateFailure);
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new IllegalStateException("payment failed for link " + code);
        }
        payments.capture(merchantId, payment.getId());

        link.markPaid(payment.getId());
        links.save(link);
        outbox.enqueue(merchantId, "paymentlink.paid", paidJson(link, chargeMinor));
        return link;
    }

    @Transactional
    public PaymentLink cancel(UUID merchantId, UUID linkId) {
        merchantScope.apply(merchantId);
        PaymentLink link = load(merchantId, linkId);
        link.cancel();
        return links.save(link);
    }

    @Transactional(readOnly = true)
    public PaymentLink getLink(UUID merchantId, UUID linkId) {
        merchantScope.apply(merchantId);
        return load(merchantId, linkId);
    }

    @Transactional(readOnly = true)
    public List<PaymentLink> listLinks(UUID merchantId) {
        merchantScope.apply(merchantId);
        return links.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /** Resolves a link by its public share code (the code carried in the shareable URL). */
    @Transactional(readOnly = true)
    public PaymentLink getByCode(UUID merchantId, String code) {
        merchantScope.apply(merchantId);
        return loadByCode(merchantId, code);
    }

    private String generateCode() {
        return "plink_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toLowerCase(Locale.ROOT);
    }

    private PaymentLink load(UUID merchantId, UUID linkId) {
        return links
                .findById(linkId)
                .filter(l -> l.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PaymentLinkNotFoundException("no payment link " + linkId));
    }

    private PaymentLink loadByCode(UUID merchantId, String code) {
        return links
                .findByMerchantIdAndCode(merchantId, code)
                .orElseThrow(() -> new PaymentLinkNotFoundException("no payment link '" + code + "'"));
    }

    private String linkJson(PaymentLink l) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("linkId", l.getId().toString());
        b.put("code", l.getCode());
        b.put("amountMinor", l.getAmountMinor());
        b.put("currency", l.getCurrency());
        return write(b);
    }

    private String paidJson(PaymentLink l, long chargedMinor) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("linkId", l.getId().toString());
        b.put("code", l.getCode());
        b.put("chargedMinor", chargedMinor);
        b.put("paymentId", l.getPaymentId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise payment-link event", e);
        }
    }
}
