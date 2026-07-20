package com.qeetgroup.qeetpay.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.payouts.PayoutRail;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payroll disbursement API (PRD Module 02.5 &amp; Module 18.4). Create stages a payroll run (all lines
 * PENDING_APPROVAL); approve (idempotent) is the maker-checker step that disburses each net pay through
 * the payouts engine and posts to the ledger; reject closes it. The active merchant comes from
 * {@link MerchantContext}.
 */
@RestController
@RequestMapping("/v1/payroll/batches")
public class PayrollController {

    private final PayrollService payroll;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public PayrollController(
            PayrollService payroll, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.payroll = payroll;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<PayrollBatchView> create(@Valid @RequestBody CreatePayrollBatchRequest request) {
        UUID merchantId = MerchantContext.require();
        List<PayrollLineInput> inputs =
                request.lines().stream()
                        .map(
                                l ->
                                        new PayrollLineInput(
                                                l.employeeRef(),
                                                l.employeeName(),
                                                l.rail(),
                                                l.destination(),
                                                l.accountNumber(),
                                                l.ifsc(),
                                                l.grossMinor(),
                                                l.pfMinor(),
                                                l.esiMinor(),
                                                l.ptMinor(),
                                                l.tdsMinor()))
                        .toList();
        PayrollBatch batch =
                payroll.create(merchantId, request.currency(), request.period(), request.description(), inputs);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(merchantId, batch));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
            throws JsonProcessingException {
        UUID merchantId = MerchantContext.require();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<IdempotencyRecord> prior = idempotency.lookup(merchantId, idempotencyKey);
            if (prior.isPresent()) {
                return ResponseEntity.status(prior.get().getResponseStatus())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(prior.get().getResponseBody());
            }
        }

        PayrollBatchView view = view(merchantId, payroll.approve(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/reject")
    public PayrollBatchView reject(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return view(merchantId, payroll.reject(merchantId, id));
    }

    @GetMapping
    public List<PayrollBatchSummaryView> list() {
        return payroll.list(MerchantContext.require()).stream().map(PayrollBatchSummaryView::of).toList();
    }

    @GetMapping("/{id}")
    public PayrollBatchView get(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return view(merchantId, payroll.get(merchantId, id));
    }

    @GetMapping("/{id}/lines")
    public List<PayrollLineView> lines(@PathVariable UUID id) {
        UUID merchantId = MerchantContext.require();
        return payroll.linesOf(merchantId, id).stream().map(PayrollLineView::of).toList();
    }

    @GetMapping("/{id}/lines/{lineId}/slip")
    public SalarySlip slip(@PathVariable UUID id, @PathVariable UUID lineId) {
        return payroll.slip(MerchantContext.require(), id, lineId);
    }

    private PayrollBatchView view(UUID merchantId, PayrollBatch batch) {
        List<PayrollLineView> lineViews =
                payroll.linesOf(merchantId, batch.getId()).stream().map(PayrollLineView::of).toList();
        return PayrollBatchView.of(batch, lineViews);
    }

    public record CreatePayrollBatchRequest(
            @NotBlank String currency,
            String period,
            String description,
            @NotEmpty @Valid List<PayrollLineDto> lines) {}

    public record PayrollLineDto(
            @NotBlank String employeeRef,
            String employeeName,
            @NotNull PayoutRail rail,
            @NotBlank String destination,
            String accountNumber,
            String ifsc,
            @Positive long grossMinor,
            @PositiveOrZero long pfMinor,
            @PositiveOrZero long esiMinor,
            @PositiveOrZero long ptMinor,
            @PositiveOrZero long tdsMinor) {}

    public record PayrollBatchView(
            String id,
            String currency,
            String period,
            String status,
            int lineCount,
            long totalGrossMinor,
            long totalStatutoryMinor,
            long totalNetMinor,
            int paidCount,
            int failedCount,
            String payoutBatchId,
            String description,
            List<PayrollLineView> lines) {

        static PayrollBatchView of(PayrollBatch b, List<PayrollLineView> lines) {
            return new PayrollBatchView(
                    b.getId().toString(),
                    b.getCurrency(),
                    b.getPeriod(),
                    b.getStatus().name(),
                    b.getLineCount(),
                    b.getTotalGrossMinor(),
                    b.getTotalStatutoryMinor(),
                    b.getTotalNetMinor(),
                    b.getPaidCount(),
                    b.getFailedCount(),
                    b.getPayoutBatchId() == null ? null : b.getPayoutBatchId().toString(),
                    b.getDescription(),
                    lines);
        }
    }

    public record PayrollBatchSummaryView(
            String id,
            String currency,
            String period,
            String status,
            int lineCount,
            long totalGrossMinor,
            long totalNetMinor,
            int paidCount,
            int failedCount,
            String description) {

        static PayrollBatchSummaryView of(PayrollBatch b) {
            return new PayrollBatchSummaryView(
                    b.getId().toString(),
                    b.getCurrency(),
                    b.getPeriod(),
                    b.getStatus().name(),
                    b.getLineCount(),
                    b.getTotalGrossMinor(),
                    b.getTotalNetMinor(),
                    b.getPaidCount(),
                    b.getFailedCount(),
                    b.getDescription());
        }
    }

    public record PayrollLineView(
            String id,
            String employeeRef,
            String employeeName,
            String rail,
            String destination,
            boolean verified,
            long grossMinor,
            long pfMinor,
            long esiMinor,
            long ptMinor,
            long tdsMinor,
            long statutoryMinor,
            long netPayMinor,
            String status,
            String payoutId,
            String ledgerEntryId,
            String failureReason) {

        static PayrollLineView of(PayrollLine l) {
            return new PayrollLineView(
                    l.getId().toString(),
                    l.getEmployeeRef(),
                    l.getEmployeeName(),
                    l.getRail().name(),
                    l.getDestination(),
                    l.isVerified(),
                    l.getGrossMinor(),
                    l.getPfMinor(),
                    l.getEsiMinor(),
                    l.getPtMinor(),
                    l.getTdsMinor(),
                    l.statutoryMinor(),
                    l.getNetPayMinor(),
                    l.getStatus().name(),
                    l.getPayoutId() == null ? null : l.getPayoutId().toString(),
                    l.getLedgerEntryId() == null ? null : l.getLedgerEntryId().toString(),
                    l.getFailureReason());
        }
    }
}
