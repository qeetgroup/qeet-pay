package com.qeetgroup.qeetpay.payouts;

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
 * Disbursements (TAD Module 02) with a maker-checker control: {@code create} only stages a payout
 * (PENDING_APPROVAL); {@code approve} disburses it via the provider and posts the ledger entry
 * (debit liability / credit bank). {@code reject} closes it without disbursing.
 */
@Service
public class PayoutService {

    private final PayoutRepository payouts;
    private final PayoutProvider provider;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public PayoutService(
            PayoutRepository payouts,
            PayoutProvider provider,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.payouts = payouts;
        this.provider = provider;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payout create(
            UUID merchantId,
            long amountMinor,
            String currency,
            PayoutRail rail,
            String destination,
            String description) {
        merchantScope.apply(merchantId);
        Payout payout =
                payouts.save(new Payout(merchantId, amountMinor, currency, rail, destination, description));
        outbox.enqueue(merchantId, "payout.created", json(payout, null));
        return payout;
    }

    /** Maker-checker approval — the only path that disburses + posts to the ledger. */
    @Transactional
    public Payout approve(UUID merchantId, UUID payoutId) {
        merchantScope.apply(merchantId);
        Payout payout = load(merchantId, payoutId);

        if (payout.getStatus() == PayoutStatus.PAID) {
            return payout; // idempotent: already disbursed, never double-post
        }
        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("cannot approve payout in status " + payout.getStatus());
        }

        PayoutProvider.ProviderResult result = provider.process(payout);
        if (!result.success()) {
            payout.markFailed(result.failureReason());
            throw new IllegalStateException("payout failed: " + result.failureReason());
        }

        UUID liability = ledger.accountByCode(merchantId, "liability").getId();
        UUID bank = ledger.accountByCode(merchantId, "bank").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "payout " + payout.getId(),
                        payout.getCurrency(),
                        List.of(
                                new LedgerLineInput(liability, Direction.DEBIT, payout.getAmountMinor()),
                                new LedgerLineInput(bank, Direction.CREDIT, payout.getAmountMinor())));

        payout.markPaid(result.providerPayoutId(), entryId);
        outbox.enqueue(merchantId, "payout.paid", json(payout, entryId));
        return payout;
    }

    @Transactional
    public Payout reject(UUID merchantId, UUID payoutId) {
        merchantScope.apply(merchantId);
        Payout payout = load(merchantId, payoutId);
        if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("cannot reject payout in status " + payout.getStatus());
        }
        payout.markRejected();
        outbox.enqueue(merchantId, "payout.rejected", json(payout, null));
        return payout;
    }

    @Transactional(readOnly = true)
    public Payout get(UUID merchantId, UUID payoutId) {
        merchantScope.apply(merchantId);
        return load(merchantId, payoutId);
    }

    private Payout load(UUID merchantId, UUID payoutId) {
        return payouts
                .findById(payoutId)
                .filter(p -> p.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PayoutNotFoundException("no payout " + payoutId));
    }

    private String json(Payout payout, UUID entryId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("payoutId", payout.getId().toString());
            body.put("amountMinor", payout.getAmountMinor());
            body.put("status", payout.getStatus().name());
            if (entryId != null) {
                body.put("ledgerEntryId", entryId.toString());
            }
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise payout event", e);
        }
    }
}
