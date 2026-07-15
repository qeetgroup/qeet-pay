package com.qeetgroup.qeetpay.filing;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GST return-filing API (TAD §7.4): prepare a GSTR-1/GSTR-3B return for a tax period from the
 * merchant's invoices, then file it to GSTN and record the ARN.
 */
@RestController
@RequestMapping("/v1/gst/returns")
public class FilingController {

    private final FilingService filing;

    public FilingController(FilingService filing) {
        this.filing = filing;
    }

    @PostMapping("/prepare")
    public ResponseEntity<ReturnView> prepare(@Valid @RequestBody PrepareRequest req) {
        GstReturnType type = req.type() == null ? GstReturnType.GSTR1 : req.type();
        FilingService.ReturnWithLines prepared =
                filing.prepareReturn(MerchantContext.require(), type, req.period());
        return ResponseEntity.ok(ReturnView.of(prepared));
    }

    @GetMapping
    public List<ReturnSummary> list() {
        return filing.listReturns(MerchantContext.require()).stream().map(ReturnSummary::of).toList();
    }

    @GetMapping("/{returnId}")
    public ReturnView get(@PathVariable UUID returnId) {
        return ReturnView.of(filing.getReturn(MerchantContext.require(), returnId));
    }

    @PostMapping("/{returnId}/file")
    public ReturnSummary file(@PathVariable UUID returnId) {
        return ReturnSummary.of(filing.fileReturn(MerchantContext.require(), returnId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record PrepareRequest(GstReturnType type, @NotBlank String period) {}

    public record ReturnLineView(
            String invoiceNumber, String buyerGstin, String placeOfSupply, String supplyType,
            long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor) {
        static ReturnLineView of(GstReturnLine l) {
            return new ReturnLineView(
                    l.getInvoiceNumber(), l.getBuyerGstin(), l.getPlaceOfSupply(), l.getSupplyType(),
                    l.getTaxableMinor(), l.getCgstMinor(), l.getSgstMinor(), l.getIgstMinor());
        }
    }

    public record ReturnSummary(
            String id, String type, String period, String status, int invoiceCount,
            long totalTaxableMinor, long totalCgstMinor, long totalSgstMinor, long totalIgstMinor,
            long totalTaxMinor, String arn, Instant preparedAt, Instant filedAt) {
        static ReturnSummary of(GstReturn r) {
            return new ReturnSummary(
                    r.getId().toString(), r.getReturnType().name(), r.getPeriod(), r.getStatus().name(),
                    r.getInvoiceCount(), r.getTotalTaxableMinor(), r.getTotalCgstMinor(), r.getTotalSgstMinor(),
                    r.getTotalIgstMinor(), r.getTotalTaxMinor(), r.getGstnArn(), r.getPreparedAt(), r.getFiledAt());
        }
    }

    public record ReturnView(ReturnSummary ret, List<ReturnLineView> lines) {
        static ReturnView of(FilingService.ReturnWithLines r) {
            return new ReturnView(
                    ReturnSummary.of(r.ret()),
                    r.lines().stream().map(ReturnLineView::of).toList());
        }
    }
}
