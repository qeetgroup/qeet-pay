package com.qeetgroup.qeetpay.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.kyb.KybVerificationAdapter;
import com.qeetgroup.qeetpay.payouts.BulkPayoutService;
import com.qeetgroup.qeetpay.payouts.Payout;
import com.qeetgroup.qeetpay.payouts.PayoutBatch;
import com.qeetgroup.qeetpay.payouts.PayoutInstruction;
import com.qeetgroup.qeetpay.payouts.PayoutStatus;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payroll disbursement (PRD Module 02.5 &amp; Module 18.4) with a maker-checker control. {@code create}
 * stages a payroll run: it validates each employee line, penny-drop verifies destination accounts via
 * the {@code kyb} module, computes net pay = gross − statutory (PF/ESI/PT/TDS) and stores everything
 * PENDING_APPROVAL — nothing disburses. {@code approve} is the only path that moves money: it hands the
 * net amounts to the existing {@code payouts} bulk engine (which posts the balanced ledger entry, debit
 * liability / credit bank) and captures each payout + ledger reference and status back onto the line.
 * {@code reject} closes the run without disbursing. This service never reimplements the payout rail.
 */
@Service
public class PayrollService {

    private final PayrollBatchRepository batches;
    private final PayrollLineRepository lines;
    private final BulkPayoutService bulkPayouts;
    private final KybVerificationAdapter kybAdapter;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    private static final String VERIFIED = "VERIFIED";

    public PayrollService(
            PayrollBatchRepository batches,
            PayrollLineRepository lines,
            BulkPayoutService bulkPayouts,
            KybVerificationAdapter kybAdapter,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.batches = batches;
        this.lines = lines;
        this.bulkPayouts = bulkPayouts;
        this.kybAdapter = kybAdapter;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /**
     * Stages a payroll run (maker step). Validates each line, penny-drop verifies any line that
     * carries an account number + IFSC, computes net pay = gross − statutory, and persists the batch
     * + lines PENDING_APPROVAL. No money moves until {@link #approve} is called.
     */
    @Transactional
    public PayrollBatch create(
            UUID merchantId,
            String currency,
            String period,
            String description,
            List<PayrollLineInput> lineInputs) {
        merchantScope.apply(merchantId);
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new IllegalArgumentException("a payroll batch needs at least one line");
        }

        Set<String> destinations = new HashSet<>();
        List<StagedLine> staged = new ArrayList<>();
        long totalGross = 0;
        long totalStatutory = 0;
        long totalNet = 0;

        for (PayrollLineInput in : lineInputs) {
            validate(in);
            if (!destinations.add(in.destination())) {
                throw new IllegalArgumentException(
                        "duplicate destination in payroll batch: " + in.destination());
            }
            long netMinor = computeNet(in);

            boolean verified = false;
            String verificationResult = null;
            if (hasAccount(in)) {
                verificationResult = kybAdapter.verifyBankAccount(in.accountNumber(), in.ifsc());
                verified = VERIFIED.equals(verificationResult);
                if (!verified) {
                    throw new IllegalArgumentException(
                            "penny-drop verification failed for " + in.employeeRef() + ": " + verificationResult);
                }
            }

            staged.add(new StagedLine(in, netMinor, verified, verificationResult));
            totalGross += in.grossMinor();
            totalStatutory += statutory(in);
            totalNet += netMinor;
        }

        PayrollBatch batch =
                batches.save(
                        new PayrollBatch(
                                merchantId,
                                currency,
                                period,
                                description,
                                staged.size(),
                                totalGross,
                                totalStatutory,
                                totalNet));

        for (StagedLine s : staged) {
            PayrollLineInput in = s.in();
            PayrollLine line =
                    new PayrollLine(
                            batch.getId(),
                            merchantId,
                            in.employeeRef(),
                            in.employeeName(),
                            in.rail(),
                            in.destination(),
                            in.accountNumber(),
                            in.ifsc(),
                            in.grossMinor(),
                            in.pfMinor(),
                            in.esiMinor(),
                            in.ptMinor(),
                            in.tdsMinor(),
                            s.netMinor());
            if (s.verificationResult() != null) {
                line.recordVerification(s.verified(), s.verificationResult());
            }
            lines.save(line);
        }

        outbox.enqueue(merchantId, "payroll_batch.created", batchJson(batch));
        return batch;
    }

    /**
     * Maker-checker approval — the only path that disburses. Each line's net pay becomes one member of
     * a {@code payouts} bulk batch; approving that batch disburses every member (posting debit
     * liability / credit bank in the ledger) with per-member failure isolation, so a single rail
     * failure yields a partially-disbursed payroll (PRD Module 02.5 edge case) rather than aborting the
     * whole run. The payout id + ledger entry id + status are captured back onto each line.
     */
    @Transactional
    public PayrollBatch approve(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        PayrollBatch batch = load(merchantId, batchId);

        if (batch.getStatus() == PayrollBatchStatus.DISBURSED
                || batch.getStatus() == PayrollBatchStatus.PARTIALLY_DISBURSED
                || batch.getStatus() == PayrollBatchStatus.FAILED) {
            return batch; // idempotent: already processed, never re-disburse
        }
        if (batch.getStatus() != PayrollBatchStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("cannot approve payroll batch in status " + batch.getStatus());
        }

        List<PayrollLine> batchLines = lines.findByBatchIdOrderByCreatedAt(batchId);

        List<PayoutInstruction> instructions = new ArrayList<>();
        for (PayrollLine line : batchLines) {
            String memo = "Salary " + line.getEmployeeRef() + (batch.getPeriod() == null ? "" : " " + batch.getPeriod());
            instructions.add(
                    new PayoutInstruction(line.getNetPayMinor(), line.getRail(), line.getDestination(), memo));
        }

        // Disburse through the existing payouts engine — never reimplement the rail or the ledger post.
        PayoutBatch payoutBatch =
                bulkPayouts.createBatch(
                        merchantId,
                        batch.getCurrency(),
                        "Payroll " + (batch.getPeriod() == null ? batch.getId() : batch.getPeriod()),
                        instructions);
        bulkPayouts.approveBatch(merchantId, payoutBatch.getId());

        Map<String, Payout> byDestination = new HashMap<>();
        for (Payout p : bulkPayouts.payoutsOf(merchantId, payoutBatch.getId())) {
            byDestination.putIfAbsent(p.getDestination(), p);
        }

        int paid = 0;
        int failed = 0;
        for (PayrollLine line : batchLines) {
            Payout p = byDestination.get(line.getDestination());
            if (p != null && p.getStatus() == PayoutStatus.PAID) {
                line.markPaid(p.getId(), p.getLedgerEntryId());
                paid++;
            } else {
                String reason =
                        p == null
                                ? "no payout produced for destination"
                                : p.getFailureReason() != null
                                        ? p.getFailureReason()
                                        : "payout not completed (" + p.getStatus() + ")";
                line.markFailed(p == null ? null : p.getId(), reason);
                failed++;
            }
            lines.save(line);
        }

        batch.complete(payoutBatch.getId(), paid, failed);
        batches.save(batch);
        outbox.enqueue(merchantId, "payroll_batch.disbursed", batchJson(batch));
        return batch;
    }

    @Transactional
    public PayrollBatch reject(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        PayrollBatch batch = load(merchantId, batchId);
        if (batch.getStatus() != PayrollBatchStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("cannot reject payroll batch in status " + batch.getStatus());
        }
        batch.reject();
        batches.save(batch);
        outbox.enqueue(merchantId, "payroll_batch.rejected", batchJson(batch));
        return batch;
    }

    @Transactional(readOnly = true)
    public PayrollBatch get(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        return load(merchantId, batchId);
    }

    @Transactional(readOnly = true)
    public List<PayrollBatch> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return batches.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public List<PayrollLine> linesOf(UUID merchantId, UUID batchId) {
        merchantScope.apply(merchantId);
        load(merchantId, batchId); // ensures the batch belongs to the merchant
        return lines.findByBatchIdOrderByCreatedAt(batchId);
    }

    /** Builds the combined salary-slip + receipt for one employee line. */
    @Transactional(readOnly = true)
    public SalarySlip slip(UUID merchantId, UUID batchId, UUID lineId) {
        merchantScope.apply(merchantId);
        PayrollBatch batch = load(merchantId, batchId);
        PayrollLine line =
                lines.findById(lineId)
                        .filter(l -> l.getBatchId().equals(batchId) && l.getMerchantId().equals(merchantId))
                        .orElseThrow(() -> new PayrollLineNotFoundException("no payroll line " + lineId));
        return SalarySlip.of(batch, line);
    }

    private PayrollBatch load(UUID merchantId, UUID batchId) {
        return batches
                .findById(batchId)
                .filter(b -> b.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PayrollBatchNotFoundException("no payroll batch " + batchId));
    }

    private static void validate(PayrollLineInput in) {
        if (in.employeeRef() == null || in.employeeRef().isBlank()) {
            throw new IllegalArgumentException("employeeRef is required");
        }
        if (in.rail() == null) {
            throw new IllegalArgumentException("rail is required for " + in.employeeRef());
        }
        if (in.destination() == null || in.destination().isBlank()) {
            throw new IllegalArgumentException("destination is required for " + in.employeeRef());
        }
        if (in.grossMinor() <= 0) {
            throw new IllegalArgumentException("gross pay must be positive for " + in.employeeRef());
        }
        if (in.pfMinor() < 0 || in.esiMinor() < 0 || in.ptMinor() < 0 || in.tdsMinor() < 0) {
            throw new IllegalArgumentException("statutory components cannot be negative for " + in.employeeRef());
        }
    }

    /** Net pay = gross − Σ statutory, in minor units, HALF_UP (no floating point). */
    private static long computeNet(PayrollLineInput in) {
        BigDecimal statutory =
                BigDecimal.valueOf(in.pfMinor())
                        .add(BigDecimal.valueOf(in.esiMinor()))
                        .add(BigDecimal.valueOf(in.ptMinor()))
                        .add(BigDecimal.valueOf(in.tdsMinor()));
        BigDecimal net = BigDecimal.valueOf(in.grossMinor()).subtract(statutory);
        long netMinor = net.setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (netMinor <= 0) {
            throw new IllegalArgumentException(
                    "net pay must be positive (gross must exceed statutory) for " + in.employeeRef());
        }
        return netMinor;
    }

    private static long statutory(PayrollLineInput in) {
        return in.pfMinor() + in.esiMinor() + in.ptMinor() + in.tdsMinor();
    }

    private static boolean hasAccount(PayrollLineInput in) {
        return in.accountNumber() != null
                && !in.accountNumber().isBlank()
                && in.ifsc() != null
                && !in.ifsc().isBlank();
    }

    private String batchJson(PayrollBatch batch) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("batchId", batch.getId().toString());
            body.put("status", batch.getStatus().name());
            body.put("lineCount", batch.getLineCount());
            body.put("totalGrossMinor", batch.getTotalGrossMinor());
            body.put("totalStatutoryMinor", batch.getTotalStatutoryMinor());
            body.put("totalNetMinor", batch.getTotalNetMinor());
            body.put("paidCount", batch.getPaidCount());
            body.put("failedCount", batch.getFailedCount());
            if (batch.getPayoutBatchId() != null) {
                body.put("payoutBatchId", batch.getPayoutBatchId().toString());
            }
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise payroll batch event", e);
        }
    }

    private record StagedLine(PayrollLineInput in, long netMinor, boolean verified, String verificationResult) {}
}
