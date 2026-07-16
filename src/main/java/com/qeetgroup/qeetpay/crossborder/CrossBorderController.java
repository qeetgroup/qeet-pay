package com.qeetgroup.qeetpay.crossborder;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-border API (PRD Module 14): raise a foreign-currency export invoice and record the foreign
 * inward remittance (FX-converted to INR, with the FIRA reference), then read invoices + remittances.
 */
@Tag(
        name = "Cross-Border",
        description = "Foreign-currency export invoices and FX-converted inward remittances (FIRA), with LUT/FEMA purpose codes.")
@RestController
@RequestMapping("/v1/crossborder/export-invoices")
public class CrossBorderController {

    private final CrossBorderService crossBorder;

    public CrossBorderController(CrossBorderService crossBorder) {
        this.crossBorder = crossBorder;
    }

    @PostMapping
    public ResponseEntity<InvoiceView> create(@Valid @RequestBody CreateExportInvoiceRequest req) {
        boolean lut = req.lut() == null || req.lut();
        ExportInvoice invoice =
                crossBorder.createExportInvoice(
                        MerchantContext.require(), req.invoiceNumber(), req.buyerCountry(), req.currency(),
                        req.foreignAmountMinor(), req.purposeCode(), lut);
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceView.of(invoice, List.of()));
    }

    @GetMapping
    public List<InvoiceSummary> list() {
        return crossBorder.listExportInvoices(MerchantContext.require()).stream().map(InvoiceSummary::of).toList();
    }

    @GetMapping("/{exportInvoiceId}")
    public InvoiceView get(@PathVariable UUID exportInvoiceId) {
        CrossBorderService.InvoiceWithRemittances iwr =
                crossBorder.getExportInvoice(MerchantContext.require(), exportInvoiceId);
        return InvoiceView.of(iwr.invoice(), iwr.remittances().stream().map(RemittanceView::of).toList());
    }

    @PostMapping("/{exportInvoiceId}/remittances")
    public ResponseEntity<RemittanceView> recordRemittance(
            @PathVariable UUID exportInvoiceId, @Valid @RequestBody RemittanceRequest req) {
        InwardRemittance r =
                crossBorder.recordRemittance(
                        MerchantContext.require(), exportInvoiceId, req.foreignAmountMinor(), req.firaReference());
        return ResponseEntity.status(HttpStatus.CREATED).body(RemittanceView.of(r));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CreateExportInvoiceRequest(
            @NotBlank String invoiceNumber,
            @NotBlank String buyerCountry,
            @NotBlank String currency,
            @NotNull @Positive Long foreignAmountMinor,
            @NotBlank String purposeCode,
            Boolean lut) {}

    public record RemittanceRequest(
            @NotNull @Positive Long foreignAmountMinor, @NotBlank String firaReference) {}

    public record RemittanceView(
            String id, long foreignAmountMinor, String foreignCurrency, BigDecimal fxRate,
            long inrAmountMinor, String firaReference, String purposeCode, String ledgerEntryId,
            Instant remittedAt) {
        static RemittanceView of(InwardRemittance r) {
            return new RemittanceView(
                    r.getId().toString(), r.getForeignAmountMinor(), r.getForeignCurrency(), r.getFxRate(),
                    r.getInrAmountMinor(), r.getFiraReference(), r.getPurposeCode(),
                    r.getLedgerEntryId().toString(), r.getRemittedAt());
        }
    }

    public record InvoiceSummary(
            String id, String invoiceNumber, String buyerCountry, String currency, long foreignAmountMinor,
            String purposeCode, boolean lut, String status, Instant createdAt) {
        static InvoiceSummary of(ExportInvoice i) {
            return new InvoiceSummary(
                    i.getId().toString(), i.getInvoiceNumber(), i.getBuyerCountry(), i.getCurrency(),
                    i.getForeignAmountMinor(), i.getPurposeCode(), i.isLut(), i.getStatus().name(), i.getCreatedAt());
        }
    }

    public record InvoiceView(InvoiceSummary invoice, List<RemittanceView> remittances) {
        static InvoiceView of(ExportInvoice i, List<RemittanceView> remittances) {
            return new InvoiceView(InvoiceSummary.of(i), remittances);
        }
    }
}
