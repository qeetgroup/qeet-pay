package com.qeetgroup.qeetpay.offline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UPI 123Pay (PRD Module 15.2) — feature-phone / IVR payments for users without a smartphone or
 * internet. An intent is created (CREATED), then confirmed (simulated IVR/missed-call completion),
 * which posts the canonical money-in entry (debit {@code settlement} / credit {@code revenue}),
 * identical to a payment capture. Merchant-scoped via RLS; outbox-published.
 */
@Service
public class Pay123Service {

    private final Pay123IntentRepository intents;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public Pay123Service(
            Pay123IntentRepository intents,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.intents = intents;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Pay123Intent createIntent(
            UUID merchantId, String payerMobile, long amountMinor, String currency) {
        merchantScope.apply(merchantId);
        if (payerMobile == null || payerMobile.isBlank()) {
            throw new IllegalArgumentException("payerMobile is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        String cur = (currency == null || currency.isBlank()) ? "INR" : currency;
        Pay123Intent intent =
                intents.save(new Pay123Intent(merchantId, payerMobile, amountMinor, cur));
        outbox.enqueue(merchantId, "pay123.intent.created", intentJson(intent));
        return intent;
    }

    /** Confirms a CREATED intent (simulated IVR completion), posting money-in to the ledger. */
    @Transactional
    public Pay123Intent confirmIntent(UUID merchantId, UUID intentId) {
        merchantScope.apply(merchantId);
        Pay123Intent intent = load(merchantId, intentId);
        if (intent.getStatus() != Pay123Status.CREATED) {
            throw new IllegalStateException(
                    "intent is not confirmable (status " + intent.getStatus() + ")");
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "123pay confirm " + intentId,
                        intent.getCurrency(),
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, intent.getAmountMinor()),
                                new LedgerLineInput(revenue, Direction.CREDIT, intent.getAmountMinor())));

        intent.confirm(entryId);
        intents.save(intent);
        outbox.enqueue(merchantId, "pay123.intent.confirmed", intentJson(intent));
        return intent;
    }

    @Transactional(readOnly = true)
    public List<Pay123Intent> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return intents.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private Pay123Intent load(UUID merchantId, UUID intentId) {
        return intents
                .findById(intentId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new OfflineNotFoundException("no 123Pay intent " + intentId));
    }

    private String intentJson(Pay123Intent i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("intentId", i.getId().toString());
        b.put("payerMobile", i.getPayerMobile());
        b.put("amountMinor", i.getAmountMinor());
        b.put("status", i.getStatus().name());
        if (i.getLedgerEntryId() != null) {
            b.put("ledgerEntryId", i.getLedgerEntryId().toString());
        }
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise 123pay event", e);
        }
    }
}
