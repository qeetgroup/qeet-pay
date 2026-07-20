package com.qeetgroup.qeetpay.ondc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.AccountType;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ONDC multi-party settlement (PRD Module 13.4). Creating an order computes each party's
 * commission/GST/TCS via {@link OndcSplitCalculator} and holds the collected gross (debit
 * {@code settlement} / credit on-demand {@code ondc_hold}). Settlement is <b>post-fulfilment</b>: it
 * releases the hold into commission revenue, tax payable, and per-party payables. Cancelling posts the
 * exact offsetting entry — the reversal of the hold when unsettled, or of the settled position once
 * settled (append-only corrections, never an UPDATE). All writes are outbox-published.
 */
@Service
public class OndcService {

    /** On-demand liability account that parks the buyer's collected funds until settlement. */
    private static final String ONDC_HOLD = "ondc_hold";

    private final OndcOrderRepository orders;
    private final OndcSettlementLineRepository lines;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public OndcService(
            OndcOrderRepository orders,
            OndcSettlementLineRepository lines,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.orders = orders;
        this.lines = lines;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Records an ONDC order, computes the per-party breakdown, and holds the collected gross. The gross
     * held equals Σ(line gross) — no paise unaccounted for.
     */
    @Transactional
    public OrderWithLines createOrder(
            UUID merchantId,
            String networkOrderId,
            String buyerApp,
            String sellerApp,
            String currency,
            List<OndcLineInput> lineInputs) {
        merchantScope.apply(merchantId);
        if (networkOrderId == null || networkOrderId.isBlank()) {
            throw new IllegalArgumentException("networkOrderId is required");
        }
        if (buyerApp == null || buyerApp.isBlank() || sellerApp == null || sellerApp.isBlank()) {
            throw new IllegalArgumentException("buyerApp and sellerApp are required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new IllegalArgumentException("an ONDC order needs at least one party line");
        }

        long gross = 0, commission = 0, commissionGst = 0, tcs = 0, net = 0;
        List<PendingLine> pending = new ArrayList<>(lineInputs.size());
        for (OndcLineInput line : lineInputs) {
            if (line.partyRef() == null || line.partyRef().isBlank()) {
                throw new IllegalArgumentException("every line needs a partyRef");
            }
            PartyRole role = line.role() != null ? line.role() : PartyRole.SELLER;
            int commissionBps = line.commissionBps() != null ? line.commissionBps() : 0;
            int commissionGstRate =
                    line.commissionGstRate() != null
                            ? line.commissionGstRate()
                            : OndcSplitCalculator.DEFAULT_COMMISSION_GST_RATE;
            int tcsBps = line.tcsBps() != null ? line.tcsBps() : OndcSplitCalculator.DEFAULT_TCS_BPS;

            OndcSplitCalculator.Breakdown b =
                    OndcSplitCalculator.compute(line.grossMinor(), commissionBps, commissionGstRate, tcsBps);
            gross += b.grossMinor();
            commission += b.commissionMinor();
            commissionGst += b.commissionGstMinor();
            tcs += b.tcsMinor();
            net += b.netMinor();
            pending.add(new PendingLine(line.partyRef(), role, commissionBps, commissionGstRate, tcsBps, b));
        }

        UUID holdEntry = postHold(merchantId, currency, gross, networkOrderId);

        OndcOrder order =
                orders.save(
                        new OndcOrder(
                                merchantId, networkOrderId, buyerApp, sellerApp, currency, gross,
                                commission, commissionGst, tcs, net, pending.size(), holdEntry));

        List<OndcSettlementLine> savedLines = new ArrayList<>(pending.size());
        for (PendingLine pl : pending) {
            savedLines.add(
                    lines.save(
                            new OndcSettlementLine(
                                    order.getId(), merchantId, pl.partyRef(), pl.role(),
                                    pl.commissionBps(), pl.commissionGstRate(), pl.tcsBps(), pl.b())));
        }

        outbox.enqueue(merchantId, "ondc.order.created", orderJson(order));
        return new OrderWithLines(order, savedLines);
    }

    /** Marks the order fulfilled on the network — the precondition for settlement. No ledger movement. */
    @Transactional
    public OndcOrder fulfill(UUID merchantId, UUID orderId) {
        merchantScope.apply(merchantId);
        OndcOrder order = load(merchantId, orderId);
        order.markFulfilled();
        orders.save(order);
        outbox.enqueue(merchantId, "ondc.order.fulfilled", orderJson(order));
        return order;
    }

    /** Settles a fulfilled order: releases the hold into revenue + tax payable + per-party payables. */
    @Transactional
    public OndcOrder settle(UUID merchantId, UUID orderId) {
        merchantScope.apply(merchantId);
        OndcOrder order = load(merchantId, orderId);
        UUID settleEntry =
                postSettle(
                        merchantId, order.getCurrency(), order.getGrossMinor(), order.getCommissionMinor(),
                        order.getCommissionGstMinor(), order.getTcsMinor(), order.getPartyNetMinor(),
                        order.getNetworkOrderId());
        order.markSettled(settleEntry);
        orders.save(order);
        outbox.enqueue(merchantId, "ondc.order.settled", orderJson(order));
        return order;
    }

    /** Cancels an order by writing the exact offsetting entry for whatever has been posted so far. */
    @Transactional
    public OndcOrder cancel(UUID merchantId, UUID orderId) {
        merchantScope.apply(merchantId);
        OndcOrder order = load(merchantId, orderId);
        if (order.getStatus() == OndcOrderStatus.CANCELLED) {
            return order; // idempotent
        }
        UUID reversal =
                order.getStatus() == OndcOrderStatus.SETTLED
                        ? postSettledReversal(merchantId, order)
                        : postHoldReversal(merchantId, order);
        order.markCancelled(reversal);
        orders.save(order);
        outbox.enqueue(merchantId, "ondc.order.cancelled", orderJson(order));
        return order;
    }

    @Transactional(readOnly = true)
    public OrderWithLines getOrder(UUID merchantId, UUID orderId) {
        merchantScope.apply(merchantId);
        OndcOrder order = load(merchantId, orderId);
        return new OrderWithLines(order, lines.findByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public List<OndcOrder> listOrders(UUID merchantId) {
        merchantScope.apply(merchantId);
        return orders.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    // ── Ledger postings ─────────────────────────────────────────────────────

    /** Hold: debit settlement Σgross / credit ondc_hold Σgross. */
    private UUID postHold(UUID merchantId, String currency, long gross, String networkOrderId) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID hold = ondcHold(merchantId, currency);
        return ledger.postEntry(
                merchantId, "ondc hold " + networkOrderId, currency,
                List.of(
                        new LedgerLineInput(settlement, Direction.DEBIT, gross),
                        new LedgerLineInput(hold, Direction.CREDIT, gross)));
    }

    /**
     * Settle: debit ondc_hold Σgross / credit revenue (Σcommission) + tax_payable (Σcommission GST +
     * Σ TCS) + liability (Σparty net). Balanced because net = gross − commission − commissionGst − tcs.
     */
    private UUID postSettle(
            UUID merchantId, String currency, long gross, long commission, long commissionGst,
            long tcs, long net, String networkOrderId) {
        UUID hold = ondcHold(merchantId, currency);
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
        UUID liability = ledger.accountByCode(merchantId, "liability").getId();

        List<LedgerLineInput> entry = new ArrayList<>();
        entry.add(new LedgerLineInput(hold, Direction.DEBIT, gross));
        if (commission > 0) {
            entry.add(new LedgerLineInput(revenue, Direction.CREDIT, commission));
        }
        long taxes = commissionGst + tcs;
        if (taxes > 0) {
            entry.add(new LedgerLineInput(taxPayable, Direction.CREDIT, taxes));
        }
        if (net > 0) {
            entry.add(new LedgerLineInput(liability, Direction.CREDIT, net));
        }
        return ledger.postEntry(merchantId, "ondc settle " + networkOrderId, currency, entry);
    }

    /** Reverses an unsettled hold: credit settlement Σgross / debit ondc_hold Σgross. */
    private UUID postHoldReversal(UUID merchantId, OndcOrder order) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID hold = ondcHold(merchantId, order.getCurrency());
        return ledger.postEntry(
                merchantId, "ondc cancel (hold) " + order.getNetworkOrderId(), order.getCurrency(),
                List.of(
                        new LedgerLineInput(settlement, Direction.CREDIT, order.getGrossMinor()),
                        new LedgerLineInput(hold, Direction.DEBIT, order.getGrossMinor())));
    }

    /**
     * Reverses a settled order's net position (the hold nets to zero once settled): credit settlement
     * Σgross / debit revenue (Σcommission) + tax_payable (Σcommission GST + Σ TCS) + liability (Σnet).
     */
    private UUID postSettledReversal(UUID merchantId, OndcOrder order) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
        UUID liability = ledger.accountByCode(merchantId, "liability").getId();

        List<LedgerLineInput> entry = new ArrayList<>();
        entry.add(new LedgerLineInput(settlement, Direction.CREDIT, order.getGrossMinor()));
        if (order.getCommissionMinor() > 0) {
            entry.add(new LedgerLineInput(revenue, Direction.DEBIT, order.getCommissionMinor()));
        }
        long taxes = order.getCommissionGstMinor() + order.getTcsMinor();
        if (taxes > 0) {
            entry.add(new LedgerLineInput(taxPayable, Direction.DEBIT, taxes));
        }
        if (order.getPartyNetMinor() > 0) {
            entry.add(new LedgerLineInput(liability, Direction.DEBIT, order.getPartyNetMinor()));
        }
        return ledger.postEntry(
                merchantId, "ondc cancel (settled) " + order.getNetworkOrderId(), order.getCurrency(), entry);
    }

    private UUID ondcHold(UUID merchantId, String currency) {
        return ledger.ensureAccount(merchantId, ONDC_HOLD, "ONDC settlement hold", AccountType.LIABILITY, currency)
                .getId();
    }

    private OndcOrder load(UUID merchantId, UUID orderId) {
        return orders
                .findById(orderId)
                .filter(o -> o.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new OrderNotFoundException("no ONDC order " + orderId));
    }

    /** An ONDC order plus its per-party settlement lines. */
    public record OrderWithLines(OndcOrder order, List<OndcSettlementLine> lines) {}

    private record PendingLine(
            String partyRef, PartyRole role, int commissionBps, int commissionGstRate,
            int tcsBps, OndcSplitCalculator.Breakdown b) {}

    private String orderJson(OndcOrder o) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("orderId", o.getId().toString());
        b.put("networkOrderId", o.getNetworkOrderId());
        b.put("grossMinor", o.getGrossMinor());
        b.put("commissionMinor", o.getCommissionMinor());
        b.put("commissionGstMinor", o.getCommissionGstMinor());
        b.put("tcsMinor", o.getTcsMinor());
        b.put("partyNetMinor", o.getPartyNetMinor());
        b.put("status", o.getStatus().name());
        b.put("holdEntryId", o.getHoldEntryId().toString());
        if (o.getSettleEntryId() != null) {
            b.put("settleEntryId", o.getSettleEntryId().toString());
        }
        if (o.getReversalEntryId() != null) {
            b.put("reversalEntryId", o.getReversalEntryId().toString());
        }
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise ondc event", e);
        }
    }
}
