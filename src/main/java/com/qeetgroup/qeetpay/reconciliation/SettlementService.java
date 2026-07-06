package com.qeetgroup.qeetpay.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Account;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests provider settlement reports (TAD §6.2). Ingestion is one atomic unit: persist the batch
 * + its lines, post the money movement to the ledger (debit bank + provider fees / credit the
 * settlement holding account), then reconcile against captured payments. Idempotent per
 * (merchant, provider, provider settlement id) — re-ingesting the same report is a no-op.
 */
@Service
public class SettlementService {

    private final SettlementRepository settlements;
    private final SettlementItemRepository items;
    private final ReconciliationService reconciliation;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public SettlementService(
            SettlementRepository settlements,
            SettlementItemRepository items,
            ReconciliationService reconciliation,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.settlements = settlements;
        this.items = items;
        this.reconciliation = reconciliation;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Settlement ingest(UUID merchantId, SettlementReport report) {
        merchantScope.apply(merchantId);
        validate(report);

        Optional<Settlement> existing =
                settlements.findByMerchantIdAndProviderAndProviderSettlementId(
                        merchantId, report.provider(), report.providerSettlementId());
        if (existing.isPresent()) {
            return existing.get(); // idempotent: this settlement report was already ingested
        }

        long gross = 0;
        long fee = 0;
        long tax = 0;
        long net = 0;
        for (SettlementReport.Line line : report.items()) {
            gross += line.grossMinor();
            fee += line.feeMinor();
            tax += line.taxMinor();
            net += line.netMinor();
        }

        Settlement settlement =
                settlements.save(
                        new Settlement(
                                merchantId,
                                report.provider(),
                                report.providerSettlementId(),
                                report.currency(),
                                gross,
                                fee,
                                tax,
                                net,
                                report.reportedNetMinor(),
                                report.items().size(),
                                report.settledAt()));

        for (SettlementReport.Line line : report.items()) {
            items.save(
                    new SettlementItem(
                            settlement.getId(),
                            merchantId,
                            line.paymentId(),
                            line.providerPaymentId(),
                            line.grossMinor(),
                            line.feeMinor(),
                            line.taxMinor(),
                            line.netMinor()));
        }

        UUID entryId = postSettlement(merchantId, settlement, gross, fee + tax, net);
        settlement.recordPosting(entryId);
        outbox.enqueue(merchantId, "settlement.received", receivedJson(settlement));

        reconciliation.reconcile(merchantId, settlement);
        return settlement;
    }

    @Transactional(readOnly = true)
    public Settlement get(UUID merchantId, UUID settlementId) {
        merchantScope.apply(merchantId);
        return settlements
                .findById(settlementId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new SettlementNotFoundException("no settlement " + settlementId));
    }

    @Transactional(readOnly = true)
    public List<Settlement> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return settlements.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /**
     * Posts the settlement to the ledger: debit bank (net received) + fees (provider cut incl. GST),
     * credit the settlement holding account (gross cleared). The lines net to zero
     * ({@code net + fee + tax = gross}); zero-value legs are omitted.
     */
    private UUID postSettlement(UUID merchantId, Settlement settlement, long gross, long feeAndTax, long net) {
        UUID settlementAccount = ledger.accountByCode(merchantId, "settlement").getId();
        UUID bank = ledger.accountByCode(merchantId, "bank").getId();

        List<LedgerLineInput> lines = new ArrayList<>();
        if (net > 0) {
            lines.add(new LedgerLineInput(bank, Direction.DEBIT, net));
        }
        if (feeAndTax > 0) {
            lines.add(new LedgerLineInput(feeAccount(merchantId, settlement.getCurrency()), Direction.DEBIT, feeAndTax));
        }
        lines.add(new LedgerLineInput(settlementAccount, Direction.CREDIT, gross));

        return ledger.postEntry(
                merchantId,
                "settlement " + settlement.getProvider() + " " + settlement.getProviderSettlementId(),
                settlement.getCurrency(),
                lines);
    }

    /** The merchant's provider-fee expense account, opened lazily for merchants onboarded before it. */
    private UUID feeAccount(UUID merchantId, String currency) {
        return ledger.accountsOf(merchantId).stream()
                .filter(a -> a.getCode().equals("fees"))
                .findFirst()
                .map(Account::getId)
                .orElseGet(
                        () ->
                                ledger.openAccount(
                                                merchantId, "fees", "Provider fees", AccountType.FEE_EXPENSE, currency)
                                        .getId());
    }

    private void validate(SettlementReport report) {
        if (report.provider() == null || report.provider().isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        if (report.providerSettlementId() == null || report.providerSettlementId().isBlank()) {
            throw new IllegalArgumentException("providerSettlementId is required");
        }
        if (report.currency() == null || report.currency().isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (report.items() == null || report.items().isEmpty()) {
            throw new IllegalArgumentException("a settlement report needs at least one line");
        }
        for (SettlementReport.Line line : report.items()) {
            if (line.grossMinor() <= 0) {
                throw new IllegalArgumentException("line gross must be positive");
            }
            if (line.feeMinor() < 0 || line.taxMinor() < 0) {
                throw new IllegalArgumentException("line fee/tax cannot be negative");
            }
            if (line.netMinor() < 0) {
                throw new IllegalArgumentException("line fee + tax cannot exceed gross");
            }
        }
    }

    private String receivedJson(Settlement settlement) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("settlementId", settlement.getId().toString());
            body.put("provider", settlement.getProvider());
            body.put("providerSettlementId", settlement.getProviderSettlementId());
            body.put("grossAmountMinor", settlement.getGrossAmountMinor());
            body.put("netAmountMinor", settlement.getNetAmountMinor());
            body.put("ledgerEntryId", settlement.getLedgerEntryId().toString());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise settlement event", e);
        }
    }
}
