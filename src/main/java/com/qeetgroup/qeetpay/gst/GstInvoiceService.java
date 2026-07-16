package com.qeetgroup.qeetpay.gst;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GST invoicing (TAD §7.2). Creating an invoice computes per-line CGST/SGST/IGST via
 * {@link GstCalculator}, assigns an FY-aware sequential number, and issues it. Paying it recognises
 * revenue + tax: a 3-line balanced ledger entry (debit settlement / credit revenue / credit
 * tax_payable), idempotent and outbox-published.
 */
@Service
public class GstInvoiceService {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final GstInvoiceRepository invoices;
    private final GstInvoiceLineRepository lines;
    private final InvoiceCounterRepository counters;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public GstInvoiceService(
            GstInvoiceRepository invoices,
            GstInvoiceLineRepository lines,
            InvoiceCounterRepository counters,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.invoices = invoices;
        this.lines = lines;
        this.counters = counters;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoiceWithLines createInvoice(
            UUID merchantId,
            String supplierGstin,
            String buyerGstin,
            String placeOfSupplyStateCode,
            String currency,
            List<GstLineInput> lineInputs) {
        merchantScope.apply(merchantId);
        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new IllegalArgumentException("an invoice needs at least one line");
        }
        if (supplierGstin == null || supplierGstin.length() < 2) {
            throw new IllegalArgumentException("supplierGstin must be a valid GSTIN");
        }

        String supplierState = supplierGstin.substring(0, 2);
        SupplyType supplyType =
                supplierState.equals(placeOfSupplyStateCode)
                        ? SupplyType.INTRA_STATE
                        : SupplyType.INTER_STATE;

        long taxable = 0, cgst = 0, sgst = 0, igst = 0;
        List<PendingLine> pending = new ArrayList<>();
        for (GstLineInput in : lineInputs) {
            if (in.quantity() <= 0 || in.unitPriceMinor() <= 0) {
                throw new IllegalArgumentException("line quantity and unit price must be positive");
            }
            long lineTaxable = Math.multiplyExact(in.unitPriceMinor(), in.quantity());
            GstCalculator.GstAmounts gst = GstCalculator.compute(lineTaxable, in.gstRate(), supplyType);
            taxable += lineTaxable;
            cgst += gst.cgstMinor();
            sgst += gst.sgstMinor();
            igst += gst.igstMinor();
            pending.add(new PendingLine(in, lineTaxable, gst));
        }

        String number = nextInvoiceNumber(merchantId);
        GstInvoice invoice =
                invoices.save(
                        new GstInvoice(
                                merchantId, number, supplierGstin, buyerGstin, placeOfSupplyStateCode,
                                supplyType, currency, taxable, cgst, sgst, igst));

        List<GstInvoiceLine> savedLines = new ArrayList<>();
        for (PendingLine pl : pending) {
            savedLines.add(
                    lines.save(
                            new GstInvoiceLine(
                                    invoice.getId(), merchantId, pl.in().description(), pl.in().hsnSac(),
                                    pl.in().quantity(), pl.in().unitPriceMinor(), pl.in().gstRate(),
                                    pl.taxable(), pl.gst())));
        }

        outbox.enqueue(merchantId, "gst.invoice.issued", issuedJson(invoice));
        return new InvoiceWithLines(invoice, savedLines);
    }

    @Transactional
    public GstInvoice payInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        GstInvoice invoice = load(merchantId, invoiceId);

        if (invoice.getStatus() == GstInvoiceStatus.PAID) {
            return invoice; // idempotent
        }
        if (invoice.getStatus() != GstInvoiceStatus.ISSUED) {
            throw new IllegalStateException("cannot pay invoice in status " + invoice.getStatus());
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue = ledger.accountByCode(merchantId, "revenue").getId();

        List<LedgerLineInput> entry = new ArrayList<>();
        entry.add(new LedgerLineInput(settlement, Direction.DEBIT, invoice.getTotalMinor()));
        entry.add(new LedgerLineInput(revenue, Direction.CREDIT, invoice.getTaxableMinor()));
        if (invoice.getTotalGstMinor() > 0) {
            UUID taxPayable = ledger.accountByCode(merchantId, "tax_payable").getId();
            entry.add(new LedgerLineInput(taxPayable, Direction.CREDIT, invoice.getTotalGstMinor()));
        }
        UUID entryId =
                ledger.postEntry(merchantId, "gst invoice " + invoice.getInvoiceNumber(), invoice.getCurrency(), entry);

        invoice.markPaid(entryId);
        outbox.enqueue(merchantId, "gst.invoice.paid", paidJson(invoice, entryId));
        return invoice;
    }

    @Transactional(readOnly = true)
    public InvoiceWithLines getInvoice(UUID merchantId, UUID invoiceId) {
        merchantScope.apply(merchantId);
        GstInvoice invoice = load(merchantId, invoiceId);
        return new InvoiceWithLines(invoice, lines.findByInvoiceId(invoiceId));
    }

    /** The merchant's invoice headers, newest-issued first (lines omitted — read them via getInvoice). */
    @Transactional(readOnly = true)
    public List<GstInvoice> list(UUID merchantId) {
        merchantScope.apply(merchantId);
        return invoices.findByMerchantIdOrderByIssuedAtDesc(merchantId);
    }

    /**
     * Invoices issued in the half-open window {@code [from, to)} — the tax-period slice the
     * {@code filing} module aggregates into a GSTR return. Read-only; never mutates.
     */
    @Transactional(readOnly = true)
    public List<GstInvoice> findIssuedInPeriod(UUID merchantId, Instant from, Instant to) {
        merchantScope.apply(merchantId);
        return invoices.findByMerchantIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThanOrderByIssuedAt(
                merchantId, from, to);
    }

    private GstInvoice load(UUID merchantId, UUID invoiceId) {
        return invoices
                .findById(invoiceId)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new GstInvoiceNotFoundException("no invoice " + invoiceId));
    }

    private String nextInvoiceNumber(UUID merchantId) {
        String fy = fiscalYear(Instant.now());
        InvoiceCounter counter =
                counters
                        .findByMerchantIdAndFiscalYear(merchantId, fy)
                        .orElseGet(() -> counters.save(new InvoiceCounter(merchantId, fy)));
        long seq = counter.next();
        counters.save(counter);
        return String.format("QP/%s/%05d", fy, seq);
    }

    /** Indian fiscal year (Apr–Mar) for an instant, e.g. {@code 2026-27}. */
    static String fiscalYear(Instant instant) {
        LocalDate d = instant.atZone(INDIA).toLocalDate();
        int startYear = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return String.format("%d-%02d", startYear, (startYear + 1) % 100);
    }

    /** A GST invoice plus its lines. */
    public record InvoiceWithLines(GstInvoice invoice, List<GstInvoiceLine> lines) {}

    private record PendingLine(GstLineInput in, long taxable, GstCalculator.GstAmounts gst) {}

    private String issuedJson(GstInvoice i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("invoiceId", i.getId().toString());
        b.put("invoiceNumber", i.getInvoiceNumber());
        b.put("totalMinor", i.getTotalMinor());
        return write(b);
    }

    private String paidJson(GstInvoice i, UUID entryId) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("invoiceId", i.getId().toString());
        b.put("invoiceNumber", i.getInvoiceNumber());
        b.put("totalMinor", i.getTotalMinor());
        b.put("ledgerEntryId", entryId.toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise gst invoice event", e);
        }
    }
}
