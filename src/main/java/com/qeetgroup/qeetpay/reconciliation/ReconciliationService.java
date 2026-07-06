package com.qeetgroup.qeetpay.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.payments.Payment;
import com.qeetgroup.qeetpay.payments.PaymentService;
import com.qeetgroup.qeetpay.payments.PaymentStatus;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles a settlement against the ledger (TAD §6.2). For each reported line it checks the
 * captured payment exists, is CAPTURED, matches on amount, and wasn't already settled; then two
 * batch invariants — the report's net control total, and the nodal check that the settlement
 * holding account never goes negative. Discrepancies are recorded for human review, not blocked:
 * the money already moved, so the settlement posting stands regardless.
 */
@Service
public class ReconciliationService {

    private final SettlementItemRepository items;
    private final ReconciliationRepository reconciliations;
    private final DiscrepancyRepository discrepancies;
    private final PaymentService payments;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public ReconciliationService(
            SettlementItemRepository items,
            ReconciliationRepository reconciliations,
            DiscrepancyRepository discrepancies,
            PaymentService payments,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.items = items;
        this.reconciliations = reconciliations;
        this.discrepancies = discrepancies;
        this.payments = payments;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Runs (once) over a freshly ingested settlement and records the outcome. */
    @Transactional
    public Reconciliation reconcile(UUID merchantId, Settlement settlement) {
        merchantScope.apply(merchantId);

        Reconciliation recon = new Reconciliation(merchantId, settlement.getId());
        List<Discrepancy> found = new ArrayList<>();
        int matched = 0;

        for (SettlementItem item : items.findBySettlementId(settlement.getId())) {
            Optional<Payment> match = payments.find(merchantId, item.getPaymentId());
            if (match.isEmpty()) {
                found.add(
                        discrepancy(
                                recon,
                                merchantId,
                                DiscrepancyType.MISSING_IN_LEDGER,
                                item.getPaymentId(),
                                item.getProviderPaymentId(),
                                null,
                                item.getGrossMinor(),
                                "settled line has no captured payment in the ledger"));
                continue;
            }

            Payment payment = match.get();
            boolean clean = true;
            if (payment.getStatus() != PaymentStatus.CAPTURED) {
                found.add(
                        discrepancy(
                                recon,
                                merchantId,
                                DiscrepancyType.STATUS_NOT_CAPTURED,
                                payment.getId(),
                                item.getProviderPaymentId(),
                                payment.getAmountMinor(),
                                item.getGrossMinor(),
                                "payment is " + payment.getStatus() + ", not CAPTURED"));
                clean = false;
            }
            if (payment.getAmountMinor() != item.getGrossMinor()) {
                found.add(
                        discrepancy(
                                recon,
                                merchantId,
                                DiscrepancyType.AMOUNT_MISMATCH,
                                payment.getId(),
                                item.getProviderPaymentId(),
                                payment.getAmountMinor(),
                                item.getGrossMinor(),
                                "captured amount does not equal settled gross"));
                clean = false;
            }
            if (items.countByMerchantIdAndPaymentId(merchantId, item.getPaymentId()) > 1) {
                found.add(
                        discrepancy(
                                recon,
                                merchantId,
                                DiscrepancyType.DUPLICATE_SETTLEMENT,
                                payment.getId(),
                                item.getProviderPaymentId(),
                                payment.getAmountMinor(),
                                item.getGrossMinor(),
                                "payment appears in more than one settlement"));
                clean = false;
            }
            if (clean) {
                matched++;
            }
        }

        // Batch control total: the report's stated net must equal the sum of the line nets.
        Long reportedNet = settlement.getReportedNetMinor();
        if (reportedNet != null && reportedNet != settlement.getNetAmountMinor()) {
            found.add(
                    discrepancy(
                            recon,
                            merchantId,
                            DiscrepancyType.BATCH_TOTAL_MISMATCH,
                            null,
                            null,
                            settlement.getNetAmountMinor(),
                            reportedNet,
                            "reported net control total does not equal the sum of line nets"));
        }

        // Nodal check (TAD §6.2): after posting, the settlement holding account must not be
        // negative — we can never settle out more than has been captured for the merchant.
        long holding =
                ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "settlement").getId());
        if (holding < 0) {
            found.add(
                    discrepancy(
                            recon,
                            merchantId,
                            DiscrepancyType.NODAL_IMBALANCE,
                            null,
                            null,
                            0L,
                            holding,
                            "settlement holding account is negative — settled more than captured"));
        }

        recon.complete(matched, found.size());
        reconciliations.save(recon);
        for (Discrepancy d : found) {
            discrepancies.save(d);
        }

        if (found.isEmpty()) {
            settlement.markReconciled();
            outbox.enqueue(merchantId, "settlement.reconciled", json(settlement, recon));
        } else {
            settlement.markDiscrepancy();
            outbox.enqueue(merchantId, "settlement.discrepancy", json(settlement, recon));
        }
        return recon;
    }

    @Transactional(readOnly = true)
    public Optional<Reconciliation> forSettlement(UUID merchantId, UUID settlementId) {
        merchantScope.apply(merchantId);
        return reconciliations.findBySettlementId(settlementId);
    }

    @Transactional(readOnly = true)
    public List<Discrepancy> discrepanciesOf(UUID merchantId, UUID reconciliationId) {
        merchantScope.apply(merchantId);
        return discrepancies.findByReconciliationId(reconciliationId);
    }

    private Discrepancy discrepancy(
            Reconciliation recon,
            UUID merchantId,
            DiscrepancyType type,
            UUID paymentId,
            String providerPaymentId,
            Long expectedMinor,
            Long reportedMinor,
            String detail) {
        return new Discrepancy(
                recon.getId(),
                merchantId,
                type,
                paymentId,
                providerPaymentId,
                expectedMinor,
                reportedMinor,
                detail);
    }

    private String json(Settlement settlement, Reconciliation recon) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("settlementId", settlement.getId().toString());
            body.put("reconciliationId", recon.getId().toString());
            body.put("status", recon.getStatus().name());
            body.put("matched", recon.getMatchedCount());
            body.put("discrepancies", recon.getDiscrepancyCount());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise reconciliation event", e);
        }
    }
}
