package com.qeetgroup.qeetpay.payouts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Bulk disbursals (TAD §17): create stages a whole batch of payouts (each PENDING_APPROVAL) via API
 * or CSV; approve is the single maker-checker step that disburses every member and records the
 * aggregate outcome; reject closes the batch without disbursing. Individual disbursal (ledger
 * posting, provider call) is delegated to {@link PayoutService#disburse}, so a single member's
 * failure is isolated — the rest still settle and the batch reports PARTIALLY_COMPLETED.
 */
@Service
public class BulkPayoutService {

    private final PayoutBatchRepository batches;
    private final PayoutRepository payouts;
    private final PayoutService payoutService;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public BulkPayoutService(
            PayoutBatchRepository batches,
            PayoutRepository payouts,
            PayoutService payoutService,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.batches = batches;
        this.payouts = payouts;
        this.payoutService = payoutService;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PayoutBatch createBatch(
            UUID merchantId, String currency, String description, List<PayoutInstruction> instructions) {
        merchantScope.apply(merchantId);
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (instructions == null || instructions.isEmpty()) {
            throw new IllegalArgumentException("a batch needs at least one payout");
        }

        long total = 0;
        for (PayoutInstruction instruction : instructions) {
            if (instruction.amountMinor() <= 0) {
                throw new IllegalArgumentException("payout amount must be positive");
            }
            if (instruction.rail() == null) {
                throw new IllegalArgumentException("payout rail is required");
            }
            if (instruction.destination() == null || instruction.destination().isBlank()) {
                throw new IllegalArgumentException("payout destination is required");
            }
            total += instruction.amountMinor();
        }

        PayoutBatch batch =
                batches.save(new PayoutBatch(merchantId, currency, description, instructions.size(), total));
        for (PayoutInstruction instruction : instructions) {
            Payout payout =
                    new Payout(
                            merchantId,
                            instruction.amountMinor(),
                            currency,
                            instruction.rail(),
                            instruction.destination(),
                            instruction.description());
            payout.assignToBatch(batch.getId());
            payouts.save(payout);
        }
        outbox.enqueue(merchantId, "payout_batch.created", json(batch));
        return batch;
    }

    @Transactional
    public PayoutBatch createBatchFromCsv(UUID merchantId, String currency, String description, String csv) {
        return createBatch(merchantId, currency, description, parseCsv(csv));
    }

    /** Maker-checker approval — the only path that disburses the batch and posts to the ledger. */
    @Transactional
    public PayoutBatch approveBatch(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        PayoutBatch batch = load(merchantId, batchId);
        if (batch.getStatus() == BatchStatus.REJECTED) {
            throw new IllegalStateException("cannot approve a rejected batch");
        }
        if (batch.getStatus() != BatchStatus.PENDING_APPROVAL) {
            return batch; // idempotent: already processed, never re-disburse
        }

        int paid = 0;
        int failed = 0;
        for (Payout payout : payouts.findByBatchId(batchId)) {
            if (payout.getStatus() == PayoutStatus.PAID) {
                paid++;
                continue;
            }
            if (payout.getStatus() != PayoutStatus.PENDING_APPROVAL) {
                failed++;
                continue;
            }
            payoutService.disburse(merchantId, payout);
            if (payout.getStatus() == PayoutStatus.PAID) {
                paid++;
            } else {
                failed++;
            }
        }

        batch.complete(paid, failed);
        outbox.enqueue(merchantId, "payout_batch.processed", json(batch));
        return batch;
    }

    @Transactional
    public PayoutBatch rejectBatch(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        PayoutBatch batch = load(merchantId, batchId);
        if (batch.getStatus() != BatchStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("cannot reject batch in status " + batch.getStatus());
        }
        for (Payout payout : payouts.findByBatchId(batchId)) {
            if (payout.getStatus() == PayoutStatus.PENDING_APPROVAL) {
                payout.markRejected();
            }
        }
        batch.reject();
        outbox.enqueue(merchantId, "payout_batch.rejected", json(batch));
        return batch;
    }

    @Transactional(readOnly = true)
    public PayoutBatch get(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        return load(merchantId, batchId);
    }

    @Transactional(readOnly = true)
    public List<PayoutBatch> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return batches.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public List<Payout> payoutsOf(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        load(merchantId, batchId); // ensures the batch belongs to the merchant
        return payouts.findByBatchId(batchId);
    }

    private PayoutBatch load(UUID merchantId, UUID batchId) {
        return batches
                .findById(batchId)
                .filter(b -> b.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PayoutBatchNotFoundException("no payout batch " + batchId));
    }

    /**
     * Parses a CSV of {@code rail,destination,amount_minor[,description]} rows. A single leading
     * header row (whose first cell isn't a rail) is tolerated; blank lines are skipped.
     */
    static List<PayoutInstruction> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("CSV body is empty");
        }
        List<PayoutInstruction> instructions = new ArrayList<>();
        String[] rows = csv.split("\\r?\\n");
        boolean headerAllowed = true;
        int lineNo = 0;
        for (String rawRow : rows) {
            lineNo++;
            String row = rawRow.trim();
            if (row.isEmpty()) {
                continue;
            }
            String[] cols = row.split(",", -1);
            PayoutRail rail = tryRail(cols[0].trim());
            if (rail == null) {
                if (headerAllowed) {
                    headerAllowed = false; // tolerate one header row
                    continue;
                }
                throw new IllegalArgumentException(
                        "invalid rail on CSV line " + lineNo + ": '" + cols[0].trim() + "'");
            }
            headerAllowed = false;
            if (cols.length < 3) {
                throw new IllegalArgumentException(
                        "CSV line " + lineNo + " needs rail,destination,amount_minor");
            }
            String destination = cols[1].trim();
            if (destination.isEmpty()) {
                throw new IllegalArgumentException("CSV line " + lineNo + " is missing a destination");
            }
            long amountMinor;
            try {
                amountMinor = Long.parseLong(cols[2].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "CSV line " + lineNo + " has a non-numeric amount_minor: '" + cols[2].trim() + "'");
            }
            String description = cols.length > 3 && !cols[3].trim().isEmpty() ? cols[3].trim() : null;
            instructions.add(new PayoutInstruction(amountMinor, rail, destination, description));
        }
        if (instructions.isEmpty()) {
            throw new IllegalArgumentException("CSV contained no payout rows");
        }
        return instructions;
    }

    private static PayoutRail tryRail(String value) {
        try {
            return PayoutRail.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String json(PayoutBatch batch) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("batchId", batch.getId().toString());
            body.put("status", batch.getStatus().name());
            body.put("totalCount", batch.getTotalCount());
            body.put("totalAmountMinor", batch.getTotalAmountMinor());
            body.put("paidCount", batch.getPaidCount());
            body.put("failedCount", batch.getFailedCount());
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise payout batch event", e);
        }
    }
}
