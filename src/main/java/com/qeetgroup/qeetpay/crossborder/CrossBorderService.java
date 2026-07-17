package com.qeetgroup.qeetpay.crossborder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-border collection (PRD Module 14, TAD §5). Raising an export invoice records a foreign-currency
 * receivable (zero-rated under LUT). Recording the inward remittance converts it to INR at the FX rate
 * from the pluggable {@link FxRateAdapter}, captures the FIRA reference, and posts the INR equivalent
 * to the ledger as money-in (debit {@code settlement} / credit {@code revenue}). Because both INR and
 * common export currencies use 1/100 minor units, INR paise = foreign minor × rate (HALF_UP).
 */
@Service
public class CrossBorderService {

    private static final String INR = "INR";

    private final ExportInvoiceRepository invoices;
    private final InwardRemittanceRepository remittances;
    private final FxRateAdapter fx;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public CrossBorderService(
            ExportInvoiceRepository invoices,
            InwardRemittanceRepository remittances,
            FxRateAdapter fx,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.invoices = invoices;
        this.remittances = remittances;
        this.fx = fx;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportInvoice createExportInvoice(
            UUID merchantId, String invoiceNumber, String buyerCountry, String currency,
            long foreignAmountMinor, String purposeCode, boolean lut) {
        merchantScope.apply(merchantId);
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("invoiceNumber is required");
        }
        if (foreignAmountMinor <= 0) {
            throw new IllegalArgumentException("foreign amount must be positive");
        }
        if (currency == null || currency.isBlank() || INR.equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("export currency must be a non-INR foreign currency");
        }
        if (purposeCode == null || purposeCode.isBlank()) {
            throw new IllegalArgumentException("FEMA purpose code is required");
        }
        ExportInvoice invoice =
                invoices.save(
                        new ExportInvoice(merchantId, invoiceNumber, buyerCountry, currency,
                                foreignAmountMinor, purposeCode, lut));
        outbox.enqueue(merchantId, "crossborder.export_invoice.issued", invoiceJson(invoice));
        return invoice;
    }

    /**
     * Records the foreign inward remittance for an export invoice: converts to INR at the current FX
     * rate, captures the FIRA reference, posts the INR money-in entry, and marks the invoice REMITTED.
     */
    @Transactional
    public InwardRemittance recordRemittance(
            UUID merchantId, UUID exportInvoiceId, long foreignAmountMinor, String firaReference) {
        merchantScope.apply(merchantId);
        if (foreignAmountMinor <= 0) {
            throw new IllegalArgumentException("remittance amount must be positive");
        }
        if (firaReference == null || firaReference.isBlank()) {
            throw new IllegalArgumentException("FIRA reference is required");
        }
        ExportInvoice invoice = load(merchantId, exportInvoiceId);
        if (invoice.getStatus() == ExportInvoiceStatus.REMITTED) {
            throw new IllegalStateException("export invoice already remitted");
        }

        BigDecimal rate = fx.rate(invoice.getCurrency(), INR);
        long inrMinor = toInrMinor(foreignAmountMinor, rate);

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "inward remittance " + firaReference, INR,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, inrMinor),
                                new LedgerLineInput(revenue, Direction.CREDIT, inrMinor)));

        InwardRemittance remittance =
                remittances.save(
                        new InwardRemittance(
                                exportInvoiceId, merchantId, foreignAmountMinor, invoice.getCurrency(),
                                rate, inrMinor, firaReference, invoice.getPurposeCode(), entryId));
        invoice.markRemitted();
        invoices.save(invoice);
        outbox.enqueue(merchantId, "crossborder.remittance.received", remittanceJson(remittance));
        return remittance;
    }

    @Transactional(readOnly = true)
    public InvoiceWithRemittances getExportInvoice(UUID merchantId, UUID exportInvoiceId) {
        merchantScope.apply(merchantId);
        ExportInvoice invoice = load(merchantId, exportInvoiceId);
        return new InvoiceWithRemittances(invoice, remittances.findByExportInvoiceIdOrderByRemittedAt(exportInvoiceId));
    }

    @Transactional(readOnly = true)
    public List<ExportInvoice> listExportInvoices(UUID merchantId) {
        merchantScope.apply(merchantId);
        return invoices.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    /** INR paise = foreign minor units · rate, HALF_UP (both currencies use 1/100 minor units). */
    static long toInrMinor(long foreignMinor, BigDecimal rate) {
        return BigDecimal.valueOf(foreignMinor).multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private ExportInvoice load(UUID merchantId, UUID exportInvoiceId) {
        return invoices
                .findById(exportInvoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new ExportInvoiceNotFoundException("no export invoice " + exportInvoiceId));
    }

    /** An export invoice plus its inward remittances. */
    public record InvoiceWithRemittances(ExportInvoice invoice, List<InwardRemittance> remittances) {}

    private String invoiceJson(ExportInvoice i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("exportInvoiceId", i.getId().toString());
        b.put("invoiceNumber", i.getInvoiceNumber());
        b.put("currency", i.getCurrency());
        b.put("foreignAmountMinor", i.getForeignAmountMinor());
        b.put("purposeCode", i.getPurposeCode());
        return write(b);
    }

    private String remittanceJson(InwardRemittance r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("remittanceId", r.getId().toString());
        b.put("exportInvoiceId", r.getExportInvoiceId().toString());
        b.put("foreignAmountMinor", r.getForeignAmountMinor());
        b.put("fxRate", r.getFxRate().toPlainString());
        b.put("inrAmountMinor", r.getInrAmountMinor());
        b.put("firaReference", r.getFiraReference());
        b.put("ledgerEntryId", r.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise cross-border event", e);
        }
    }
}
