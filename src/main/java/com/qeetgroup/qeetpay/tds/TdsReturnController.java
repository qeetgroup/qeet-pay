package com.qeetgroup.qeetpay.tds;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TDS/TCS statutory quarterly returns API (PRD Module 06.4): prepare a Form 24Q/26Q/27EQ return by
 * aggregating a quarter's tax-at-source deductions, list/read prepared returns, download the NSDL
 * FVU-style export, and file the return to the TIN gateway (acknowledgement / provisional receipt).
 */
@Tag(
        name = "TDS / TCS Returns",
        description = "Prepare, export, and file quarterly TDS/TCS statutory returns (Form 24Q/26Q/27EQ).")
@RestController
@RequestMapping("/v1/tds/returns")
public class TdsReturnController {

    private final TdsReturnService returns;

    public TdsReturnController(TdsReturnService returns) {
        this.returns = returns;
    }

    @PostMapping("/prepare")
    public ResponseEntity<ReturnView> prepare(@Valid @RequestBody PrepareRequest req) {
        TdsReturnForm form = req.form() == null ? TdsReturnForm.FORM_26Q : req.form();
        TdsReturnService.ReturnWithLines prepared =
                returns.prepareReturn(MerchantContext.require(), form, req.quarter());
        return ResponseEntity.ok(ReturnView.of(prepared));
    }

    @GetMapping
    public List<ReturnSummary> list() {
        return returns.listReturns(MerchantContext.require()).stream().map(ReturnSummary::of).toList();
    }

    @GetMapping("/{returnId}")
    public ReturnView get(@PathVariable UUID returnId) {
        return ReturnView.of(returns.getReturn(MerchantContext.require(), returnId));
    }

    @GetMapping("/{returnId}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID returnId) {
        UUID merchantId = MerchantContext.require();
        TdsReturn ret = returns.getReturn(merchantId, returnId).ret();
        String body = returns.export(merchantId, returnId);
        String filename = ret.getForm().code() + "_" + ret.getFy() + "_" + ret.getQuarter() + ".txt";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{returnId}/file")
    public ReturnSummary file(@PathVariable UUID returnId) {
        return ReturnSummary.of(returns.fileReturn(MerchantContext.require(), returnId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    @Schema(name = "TdsPrepareRequest")
    public record PrepareRequest(TdsReturnForm form, @NotBlank String quarter) {}

    @Schema(name = "TdsReturnLineView")
    public record ReturnLineView(
            String deducteeName, String deducteePan, String section, long grossMinor, int rateBps,
            long taxMinor, LocalDate deductedOn, String transactionRef) {
        static ReturnLineView of(TdsReturnLine l) {
            return new ReturnLineView(
                    l.getDeducteeName(), l.getDeducteePan(), l.getSection(), l.getGrossMinor(),
                    l.getRateBps(), l.getTaxMinor(), l.getDeductedOn(), l.getTransactionRef());
        }
    }

    @Schema(name = "TdsReturnSummary")
    public record ReturnSummary(
            String id, String form, String fy, String quarter, String status, int deducteeCount,
            int deductionCount, long totalGrossMinor, long totalTaxMinor, String bsrCode,
            String challanNo, LocalDate challanDate, String ackToken, Instant preparedAt,
            Instant filedAt, Instant createdAt) {
        static ReturnSummary of(TdsReturn r) {
            return new ReturnSummary(
                    r.getId().toString(), r.getForm().code(), r.getFy(), r.getQuarter(),
                    r.getStatus().name(), r.getDeducteeCount(), r.getDeductionCount(),
                    r.getTotalGrossMinor(), r.getTotalTaxMinor(), r.getBsrCode(), r.getChallanNo(),
                    r.getChallanDate(), r.getAckToken(), r.getPreparedAt(), r.getFiledAt(),
                    r.getCreatedAt());
        }
    }

    @Schema(name = "TdsReturnView")
    public record ReturnView(ReturnSummary ret, List<ReturnLineView> lines) {
        static ReturnView of(TdsReturnService.ReturnWithLines r) {
            return new ReturnView(
                    ReturnSummary.of(r.ret()),
                    r.lines().stream().map(ReturnLineView::of).toList());
        }
    }
}
