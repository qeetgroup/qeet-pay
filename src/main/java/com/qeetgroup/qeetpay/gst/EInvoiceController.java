package com.qeetgroup.qeetpay.gst;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-invoicing API (TAD §7.3): register an issued GST invoice at the IRP to obtain its IRN + signed
 * QR, read the IRN details, and cancel the IRN within the regulatory window.
 */
@RestController
@RequestMapping("/v1/gst/invoices/{invoiceId}/irn")
public class EInvoiceController {

    private final EInvoiceService eInvoice;

    public EInvoiceController(EInvoiceService eInvoice) {
        this.eInvoice = eInvoice;
    }

    @PostMapping
    public IrnView generate(@PathVariable UUID invoiceId) {
        return IrnView.of(eInvoice.generateIrn(MerchantContext.require(), invoiceId));
    }

    @GetMapping
    public IrnView get(@PathVariable UUID invoiceId) {
        return IrnView.of(eInvoice.getEInvoice(MerchantContext.require(), invoiceId));
    }

    @PostMapping("/cancel")
    public IrnView cancel(@PathVariable UUID invoiceId, @Valid @RequestBody CancelRequest req) {
        return IrnView.of(eInvoice.cancelIrn(MerchantContext.require(), invoiceId, req.reason()));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record CancelRequest(@NotBlank String reason) {}

    public record IrnView(
            String invoiceId, String invoiceNumber, String irnStatus, String irn,
            String ackNo, Instant ackDate, String signedQrCode,
            Instant generatedAt, Instant cancelledAt, String cancelReason) {
        static IrnView of(GstInvoice i) {
            return new IrnView(
                    i.getId().toString(), i.getInvoiceNumber(), i.getIrnStatus().name(), i.getIrn(),
                    i.getIrpAckNo(), i.getIrpAckDate(), i.getSignedQrCode(),
                    i.getIrnGeneratedAt(), i.getIrnCancelledAt(), i.getIrnCancelReason());
        }
    }
}
