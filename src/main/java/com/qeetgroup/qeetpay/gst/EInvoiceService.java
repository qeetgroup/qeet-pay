package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * E-invoicing / IRN registration (TAD §7.3). Registers an issued GST invoice at the IRP through the
 * pluggable {@link IrpAdapter} to obtain an IRN + signed QR, and cancels an IRN within the
 * regulatory window. Generation is idempotent (a re-request on an already-registered invoice returns
 * the existing IRN). Every state change is outbox-published. Reuses the {@code gst} module's own
 * repositories; the ledger is untouched (IRN registration moves no money).
 */
@Service
public class EInvoiceService {

    private final GstInvoiceRepository invoices;
    private final GstInvoiceLineRepository lines;
    private final IrpAdapter irp;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public EInvoiceService(
            GstInvoiceRepository invoices,
            GstInvoiceLineRepository lines,
            IrpAdapter irp,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.invoices = invoices;
        this.lines = lines;
        this.irp = irp;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GstInvoice generateIrn(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        GstInvoice invoice = load(merchantId, invoiceId);
        if (invoice.getIrnStatus() == IrnStatus.GENERATED) {
            return invoice; // idempotent
        }
        List<GstInvoiceLine> invoiceLines = lines.findByInvoiceId(invoiceId);
        IrpResult result = irp.generateIrn(invoice, invoiceLines);
        invoice.applyIrn(result);
        outbox.enqueue(merchantId, "gst.einvoice.generated", irnJson(invoice));
        return invoice;
    }

    @Transactional
    public GstInvoice cancelIrn(UUID merchantId, UUID invoiceId, String reason) {
        merchantScope.apply(merchantId);
        GstInvoice invoice = load(merchantId, invoiceId);
        if (invoice.getIrnStatus() != IrnStatus.GENERATED) {
            throw new IllegalStateException("no active IRN to cancel");
        }
        irp.cancelIrn(invoice.getIrn(), reason);
        invoice.cancelIrn(reason);
        outbox.enqueue(merchantId, "gst.einvoice.cancelled", irnJson(invoice));
        return invoice;
    }

    @Transactional(readOnly = true)
    public GstInvoice getEInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        return load(merchantId, invoiceId);
    }

    private GstInvoice load(UUID merchantId, UUID invoiceId) {
        return invoices
                .findById(invoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new GstInvoiceNotFoundException("no invoice " + invoiceId));
    }

    private String irnJson(GstInvoice i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("invoiceId", i.getId().toString());
        b.put("invoiceNumber", i.getInvoiceNumber());
        b.put("irn", i.getIrn());
        b.put("irnStatus", i.getIrnStatus().name());
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise e-invoice event", e);
        }
    }
}
