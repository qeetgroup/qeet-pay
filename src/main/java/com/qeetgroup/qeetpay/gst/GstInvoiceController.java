package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyRecord;
import com.qeetgroup.qeetpay.platform.idempotency.IdempotencyService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
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
 * GST invoicing API (TAD Module 05). Create computes the GST breakup + assigns a number; pay
 * (idempotent) posts the 3-line ledger entry. Active merchant from {@link MerchantContext}.
 */
@Tag(
        name = "GST Invoicing",
        description = "Create GST invoices (auto CGST/SGST/IGST breakup + numbering) and record idempotent invoice payment.")
@RestController
@RequestMapping("/v1/gst/invoices")
public class GstInvoiceController {

    private final GstInvoiceService service;
    private final IdempotencyService idempotency;
    private final ObjectMapper objectMapper;

    public GstInvoiceController(
            GstInvoiceService service, IdempotencyService idempotency, ObjectMapper objectMapper) {
        this.service = service;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<InvoiceView> create(@Valid @RequestBody CreateInvoiceRequest req) {
        GstInvoiceService.InvoiceWithLines result =
                service.createInvoice(
                        MerchantContext.require(),
                        req.supplierGstin(),
                        req.buyerGstin(),
                        req.placeOfSupply(),
                        req.currency(),
                        req.lines().stream()
                                .map(l -> new GstLineInput(l.description(), l.hsnSac(), l.quantity(), l.unitPriceMinor(), l.gstRate()))
                                .toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceView.of(result));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> pay(
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

        InvoiceView view = InvoiceView.ofHeader(service.payInvoice(merchantId, id));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotency.save(
                    merchantId, idempotencyKey, HttpStatus.OK.value(), objectMapper.writeValueAsString(view));
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping
    public List<InvoiceView> list() {
        return service.list(MerchantContext.require()).stream().map(InvoiceView::ofHeader).toList();
    }

    @GetMapping("/{id}")
    public InvoiceView get(@PathVariable UUID id) {
        return InvoiceView.of(service.getInvoice(MerchantContext.require(), id));
    }

    public record CreateInvoiceRequest(
            @NotBlank String supplierGstin,
            String buyerGstin,
            @NotBlank String placeOfSupply,
            @NotBlank String currency,
            @NotEmpty List<LineDto> lines) {}

    public record LineDto(
            @NotBlank String description,
            @NotBlank String hsnSac,
            @Positive long quantity,
            @Positive long unitPriceMinor,
            @Min(0) int gstRate) {}

    public record LineView(
            String description, String hsnSac, long taxableMinor, long cgstMinor, long sgstMinor, long igstMinor, long lineTotalMinor) {
        static LineView of(GstInvoiceLine l) {
            return new LineView(
                    l.getDescription(), l.getHsnSac(), l.getTaxableMinor(),
                    l.getCgstMinor(), l.getSgstMinor(), l.getIgstMinor(), l.getLineTotalMinor());
        }
    }

    public record InvoiceView(
            String id,
            String invoiceNumber,
            String supplyType,
            String status,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            long totalGstMinor,
            long totalMinor,
            String ledgerEntryId,
            List<LineView> lines) {

        static InvoiceView of(GstInvoiceService.InvoiceWithLines r) {
            return base(r.invoice(), r.lines().stream().map(LineView::of).toList());
        }

        static InvoiceView ofHeader(GstInvoice i) {
            return base(i, List.of());
        }

        private static InvoiceView base(GstInvoice i, List<LineView> lines) {
            return new InvoiceView(
                    i.getId().toString(),
                    i.getInvoiceNumber(),
                    i.getSupplyType().name(),
                    i.getStatus().name(),
                    i.getTaxableMinor(),
                    i.getCgstMinor(),
                    i.getSgstMinor(),
                    i.getIgstMinor(),
                    i.getTotalGstMinor(),
                    i.getTotalMinor(),
                    i.getLedgerEntryId() == null ? null : i.getLedgerEntryId().toString(),
                    lines);
        }
    }
}
