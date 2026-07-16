package com.qeetgroup.qeetpay.esg;

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
 * ESG / carbon-footprint tracking (PRD Module 16, TAD §5). Recording a payment's footprint via {@link
 * CarbonCalculator} is informational and never touches the ledger; purchasing an offset costs money and
 * posts a balanced entry (debit {@code fees} / credit {@code settlement}) for its cost. Carbon records
 * and offsets are append-only, and every write emits an outbox event.
 */
@Service
public class EsgService {

    private final CarbonRecordRepository records;
    private final CarbonOffsetRepository offsets;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public EsgService(
            CarbonRecordRepository records,
            CarbonOffsetRepository offsets,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.records = records;
        this.offsets = offsets;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Estimates and records the carbon footprint of a payment (no ledger impact). */
    @Transactional
    public CarbonRecord recordFootprint(
            UUID merchantId, String transactionRef, CarbonMethod method, long amountMinor) {
        merchantScope.apply(merchantId);
        if (transactionRef == null || transactionRef.isBlank()) {
            throw new IllegalArgumentException("transactionRef is required");
        }
        if (method == null) {
            throw new IllegalArgumentException("method is required");
        }
        if (amountMinor < 0) {
            throw new IllegalArgumentException("amountMinor must be non-negative");
        }
        long grams = CarbonCalculator.gramsCo2(method, amountMinor);
        CarbonRecord record =
                records.save(new CarbonRecord(merchantId, transactionRef, method, amountMinor, grams));
        outbox.enqueue(merchantId, "esg.footprint.recorded", recordJson(record));
        return record;
    }

    /**
     * Buys a carbon offset for {@code gramsToOffset} grams at {@code pricePerTonneMinor}. When the cost
     * is positive it posts a balanced entry (debit {@code fees} / credit {@code settlement}); a
     * zero-cost offset is still recorded but skips the ledger.
     */
    @Transactional
    public CarbonOffset purchaseOffset(
            UUID merchantId, long gramsToOffset, String currency, long pricePerTonneMinor) {
        merchantScope.apply(merchantId);
        if (gramsToOffset <= 0) {
            throw new IllegalArgumentException("gramsToOffset must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (pricePerTonneMinor < 0) {
            throw new IllegalArgumentException("pricePerTonneMinor must be non-negative");
        }

        long cost = CarbonCalculator.offsetCostMinor(gramsToOffset, pricePerTonneMinor);
        UUID entryId = cost > 0 ? postOffsetCost(merchantId, currency, cost, gramsToOffset) : null;
        String note = cost > 0 ? null : "zero-cost offset (no ledger posting)";
        CarbonOffset offset =
                offsets.save(new CarbonOffset(merchantId, gramsToOffset, cost, currency, entryId, note));
        outbox.enqueue(merchantId, "esg.offset.purchased", offsetJson(offset));
        return offset;
    }

    /** Aggregate footprint position: gross emissions recorded, grams offset, and the net balance. */
    @Transactional(readOnly = true)
    public FootprintSummary footprintSummary(UUID merchantId) {
        merchantScope.apply(merchantId);
        long recordCount = records.countByMerchantId(merchantId);
        long totalGramsCo2 = records.sumGramsCo2(merchantId);
        long totalGramsOffset = offsets.sumGramsOffset(merchantId);
        return new FootprintSummary(
                recordCount, totalGramsCo2, totalGramsOffset, totalGramsCo2 - totalGramsOffset);
    }

    @Transactional(readOnly = true)
    public List<CarbonRecord> listRecords(UUID merchantId) {
        merchantScope.apply(merchantId);
        return records.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    // ── Ledger postings ──────────────────────────────────────────────────────

    private UUID postOffsetCost(UUID merchantId, String currency, long cost, long grams) {
        UUID fees = ledger.accountByCode(merchantId, "fees").getId();
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        return ledger.postEntry(
                merchantId,
                "carbon offset " + grams + "g",
                currency,
                List.of(
                        new LedgerLineInput(fees, Direction.DEBIT, cost),
                        new LedgerLineInput(settlement, Direction.CREDIT, cost)));
    }

    /** Gross emissions recorded, grams offset, and the net (gross − offset) footprint, all in grams. */
    public record FootprintSummary(
            long recordCount, long totalGramsCo2, long totalGramsOffset, long netGramsCo2) {}

    private String recordJson(CarbonRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("recordId", r.getId().toString());
        b.put("transactionRef", r.getTransactionRef());
        b.put("method", r.getMethod().name());
        b.put("amountMinor", r.getAmountMinor());
        b.put("gramsCo2", r.getGramsCo2());
        return write(b);
    }

    private String offsetJson(CarbonOffset o) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("offsetId", o.getId().toString());
        b.put("gramsCo2Offset", o.getGramsCo2Offset());
        b.put("costMinor", o.getCostMinor());
        b.put("ledgerEntryId", o.getLedgerEntryId() == null ? null : o.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise esg event", e);
        }
    }
}
