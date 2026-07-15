package com.qeetgroup.qeetpay.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Marketplace split settlements (TAD §5 "Marketplace"). Registering a seller records commission
 * defaults; creating a split computes each seller's commission/GST/TCS/TDS via {@link SplitCalculator}
 * and posts one balanced ledger entry that reclassifies the collected gross into commission revenue,
 * tax payable, and seller payables. Cancelling posts the exact offsetting entry (append-only
 * corrections). All writes are outbox-published.
 */
@Service
public class MarketplaceService {

    private final MarketplaceSellerRepository sellers;
    private final SplitPaymentRepository splits;
    private final SplitItemRepository items;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public MarketplaceService(
            MarketplaceSellerRepository sellers,
            SplitPaymentRepository splits,
            SplitItemRepository items,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.sellers = sellers;
        this.splits = splits;
        this.items = items;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MarketplaceSeller registerSeller(
            UUID merchantId, String sellerRef, String name, String gstin, String pan, int commissionBps) {
        merchantScope.apply(merchantId);
        if (sellerRef == null || sellerRef.isBlank()) {
            throw new IllegalArgumentException("sellerRef is required");
        }
        sellers.findByMerchantIdAndSellerRef(merchantId, sellerRef).ifPresent(s -> {
            throw new IllegalStateException("seller '" + sellerRef + "' already registered");
        });
        MarketplaceSeller seller =
                sellers.save(new MarketplaceSeller(merchantId, sellerRef, name, gstin, pan, commissionBps));
        outbox.enqueue(merchantId, "marketplace.seller.registered", sellerJson(seller));
        return seller;
    }

    @Transactional
    public MarketplaceSeller setSellerStatus(UUID merchantId, UUID sellerId, boolean active) {
        merchantScope.apply(merchantId);
        MarketplaceSeller seller = loadSeller(merchantId, sellerId);
        if (active) {
            seller.activate();
        } else {
            seller.suspend();
        }
        return sellers.save(seller);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceSeller> listSellers(UUID merchantId) {
        merchantScope.apply(merchantId);
        return sellers.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Splits a collected payment across sellers, posts the balanced ledger entry, and persists the
     * per-seller breakdown. The collected gross must equal Σ(line gross) — no paise unaccounted for.
     */
    @Transactional
    public SplitWithItems createSplit(
            UUID merchantId, UUID paymentId, String sourceRef, String currency, List<SplitLineInput> lines) {
        merchantScope.apply(merchantId);
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("a split needs at least one seller line");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        long gross = 0, commission = 0, commissionGst = 0, tcs = 0, tds = 0, net = 0;
        List<PendingItem> pending = new ArrayList<>(lines.size());
        for (SplitLineInput line : lines) {
            MarketplaceSeller seller = loadSellerByRef(merchantId, line.sellerRef());
            if (!seller.isActive()) {
                throw new IllegalStateException("seller '" + line.sellerRef() + "' is suspended");
            }
            int commissionBps = line.commissionBps() != null ? line.commissionBps() : seller.getCommissionBps();
            int commissionGstRate =
                    line.commissionGstRate() != null
                            ? line.commissionGstRate()
                            : SplitCalculator.DEFAULT_COMMISSION_GST_RATE;
            int tcsBps = line.tcsBps() != null ? line.tcsBps() : SplitCalculator.DEFAULT_TCS_BPS;
            int tdsBps = line.tdsBps() != null ? line.tdsBps() : SplitCalculator.DEFAULT_TDS_BPS;

            SplitCalculator.Breakdown b =
                    SplitCalculator.compute(line.grossMinor(), commissionBps, commissionGstRate, tcsBps, tdsBps);
            gross += b.grossMinor();
            commission += b.commissionMinor();
            commissionGst += b.commissionGstMinor();
            tcs += b.tcsMinor();
            tds += b.tdsMinor();
            net += b.netMinor();
            pending.add(new PendingItem(seller, commissionBps, commissionGstRate, tcsBps, tdsBps, b));
        }

        UUID ledgerEntry = postSplit(merchantId, currency, gross, commission, commissionGst, tcs, tds, net, false, sourceRef);

        SplitPayment split =
                splits.save(
                        new SplitPayment(
                                merchantId, paymentId, sourceRef, currency, gross, commission,
                                commissionGst, tcs, tds, net, pending.size(), ledgerEntry));

        List<SplitItem> savedItems = new ArrayList<>(pending.size());
        for (PendingItem pi : pending) {
            savedItems.add(
                    items.save(
                            new SplitItem(
                                    split.getId(), merchantId, pi.seller().getId(), pi.seller().getSellerRef(),
                                    pi.commissionBps(), pi.commissionGstRate(), pi.tcsBps(), pi.tdsBps(), pi.b())));
        }

        outbox.enqueue(merchantId, "marketplace.split.posted", splitJson(split));
        return new SplitWithItems(split, savedItems);
    }

    /** Cancels a posted split by writing the exact offsetting ledger entry (never mutating the original). */
    @Transactional
    public SplitPayment cancelSplit(UUID merchantId, UUID splitId) {
        merchantScope.apply(merchantId);
        SplitPayment split = loadSplit(merchantId, splitId);
        if (split.getStatus() == SplitStatus.CANCELLED) {
            return split; // idempotent
        }
        UUID reversal =
                postSplit(
                        merchantId, split.getCurrency(), split.getGrossMinor(), split.getCommissionMinor(),
                        split.getCommissionGstMinor(), split.getTcsMinor(), split.getTdsMinor(),
                        split.getSellerNetMinor(), true, "reversal of " + splitId);
        split.markCancelled(reversal);
        splits.save(split);
        outbox.enqueue(merchantId, "marketplace.split.cancelled", splitJson(split));
        return split;
    }

    @Transactional(readOnly = true)
    public SplitWithItems getSplit(UUID merchantId, UUID splitId) {
        merchantScope.apply(merchantId);
        SplitPayment split = loadSplit(merchantId, splitId);
        return new SplitWithItems(split, items.findBySplitId(splitId));
    }

    @Transactional(readOnly = true)
    public List<SplitPayment> listSplits(UUID merchantId) {
        merchantScope.apply(merchantId);
        return splits.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Posts (or, when {@code reverse}, un-posts) the split money movement:
     * debit settlement Σgross / credit revenue (commission) + tax_payable (gst+tcs+tds) + liability (net).
     */
    private UUID postSplit(
            UUID merchantId, String currency, long gross, long commission, long commissionGst,
            long tcs, long tds, long net, boolean reverse, String description) {
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
        UUID liability = ledger.accountByCode(merchantId, "liability").getId();

        Direction settleSide = reverse ? Direction.CREDIT : Direction.DEBIT;
        Direction otherSide = reverse ? Direction.DEBIT : Direction.CREDIT;

        List<LedgerLineInput> entry = new ArrayList<>();
        entry.add(new LedgerLineInput(settlement, settleSide, gross));
        if (commission > 0) {
            entry.add(new LedgerLineInput(revenue, otherSide, commission));
        }
        long taxes = commissionGst + tcs + tds;
        if (taxes > 0) {
            entry.add(new LedgerLineInput(taxPayable, otherSide, taxes));
        }
        if (net > 0) {
            entry.add(new LedgerLineInput(liability, otherSide, net));
        }
        return ledger.postEntry(merchantId, "marketplace split " + description, currency, entry);
    }

    private MarketplaceSeller loadSeller(UUID merchantId, UUID sellerId) {
        return sellers
                .findById(sellerId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new SellerNotFoundException("no seller " + sellerId));
    }

    private MarketplaceSeller loadSellerByRef(UUID merchantId, String sellerRef) {
        return sellers
                .findByMerchantIdAndSellerRef(merchantId, sellerRef)
                .orElseThrow(() -> new SellerNotFoundException("no seller '" + sellerRef + "'"));
    }

    private SplitPayment loadSplit(UUID merchantId, UUID splitId) {
        return splits
                .findById(splitId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new SplitNotFoundException("no split " + splitId));
    }

    /** A split plus its per-seller items. */
    public record SplitWithItems(SplitPayment split, List<SplitItem> items) {}

    private record PendingItem(
            MarketplaceSeller seller, int commissionBps, int commissionGstRate,
            int tcsBps, int tdsBps, SplitCalculator.Breakdown b) {}

    private String sellerJson(MarketplaceSeller s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("sellerId", s.getId().toString());
        b.put("sellerRef", s.getSellerRef());
        return write(b);
    }

    private String splitJson(SplitPayment s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("splitId", s.getId().toString());
        b.put("grossMinor", s.getGrossMinor());
        b.put("commissionMinor", s.getCommissionMinor());
        b.put("tcsMinor", s.getTcsMinor());
        b.put("tdsMinor", s.getTdsMinor());
        b.put("sellerNetMinor", s.getSellerNetMinor());
        b.put("status", s.getStatus().name());
        b.put("ledgerEntryId", s.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise marketplace event", e);
        }
    }
}
