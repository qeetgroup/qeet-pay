package com.qeetgroup.qeetpay.itc;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
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
 * Input Tax Credit API (PRD Module 05/06): record inward-supply purchase invoices, reconcile them
 * against supplier-filed GSTR-2B data, and report the eligible ITC. No money movement.
 */
@Tag(
        name = "Input Tax Credit",
        description = "Record inward-supply purchase invoices, reconcile them against supplier-filed GSTR-2B, and report eligible ITC.")
@RestController
@RequestMapping("/v1/itc")
public class ItcController {

    private final ItcService itc;

    public ItcController(ItcService itc) {
        this.itc = itc;
    }

    @PostMapping("/purchases")
    public ResponseEntity<PurchaseView> record(@Valid @RequestBody RecordPurchaseRequest req) {
        PurchaseInvoice invoice =
                itc.recordPurchase(
                        MerchantContext.require(), req.supplierGstin(), req.supplierName(),
                        req.invoiceNumber(), req.invoiceDate(), req.taxableMinor(), req.cgstMinor(),
                        req.sgstMinor(), req.igstMinor(), req.itcEligible());
        return ResponseEntity.ok(PurchaseView.of(invoice));
    }

    @GetMapping("/purchases")
    public List<PurchaseView> list() {
        return itc.listPurchases(MerchantContext.require()).stream().map(PurchaseView::of).toList();
    }

    @GetMapping("/purchases/{id}")
    public PurchaseView get(@PathVariable UUID id) {
        return PurchaseView.of(itc.getPurchase(MerchantContext.require(), id));
    }

    @PostMapping("/reconcile")
    public ItcService.ReconSummary reconcile(@Valid @RequestBody ReconcileRequest req) {
        return itc.reconcileAgainst2b(MerchantContext.require(), req.lines());
    }

    @GetMapping("/eligible-summary")
    public ItcService.EligibleItcSummary eligibleSummary() {
        return itc.eligibleItcSummary(MerchantContext.require());
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record RecordPurchaseRequest(
            @NotBlank String supplierGstin,
            @NotBlank String supplierName,
            @NotBlank String invoiceNumber,
            @NotNull LocalDate invoiceDate,
            @PositiveOrZero long taxableMinor,
            @PositiveOrZero long cgstMinor,
            @PositiveOrZero long sgstMinor,
            @PositiveOrZero long igstMinor,
            boolean itcEligible) {}

    public record ReconcileRequest(@NotNull List<Gstr2bLine> lines) {}

    public record PurchaseView(
            String id, String supplierGstin, String supplierName, String invoiceNumber,
            LocalDate invoiceDate, long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor,
            long totalGstMinor, boolean itcEligible, String reconStatus, Instant createdAt,
            Instant reconciledAt) {
        static PurchaseView of(PurchaseInvoice i) {
            return new PurchaseView(
                    i.getId().toString(), i.getSupplierGstin(), i.getSupplierName(), i.getInvoiceNumber(),
                    i.getInvoiceDate(), i.getTaxableMinor(), i.getCgstMinor(), i.getSgstMinor(),
                    i.getIgstMinor(), i.getTotalGstMinor(), i.isItcEligible(), i.getReconStatus().name(),
                    i.getCreatedAt(), i.getReconciledAt());
        }
    }
}
