package com.qeetgroup.qeetpay.itc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Input Tax Credit tracking &amp; GSTR-2B reconciliation (PRD Module 05/06). Records the merchant's
 * inward-supply purchase invoices, then reconciles each against supplier-filed GSTR-2B data: a line
 * matching on (supplier GSTIN, invoice number) with equal GST is MATCHED, a differing GST is
 * MISMATCHED, and an absent line is MISSING_IN_2B. Eligible ITC sums the GST of invoices that are both
 * MATCHED and flagged itc_eligible. Pure compliance tracking — no ledger postings.
 */
@Service
public class ItcService {

    private final PurchaseInvoiceRepository invoices;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public ItcService(
            PurchaseInvoiceRepository invoices,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.invoices = invoices;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Records an inward-supply purchase invoice, initially UNMATCHED against GSTR-2B. */
    @Transactional
    public PurchaseInvoice recordPurchase(
            UUID merchantId,
            String supplierGstin,
            String supplierName,
            String invoiceNumber,
            LocalDate invoiceDate,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            boolean itcEligible) {
        merchantScope.apply(merchantId);
        if (taxableMinor < 0 || cgstMinor < 0 || sgstMinor < 0 || igstMinor < 0) {
            throw new IllegalArgumentException("amounts must be >= 0");
        }
        PurchaseInvoice invoice =
                invoices.save(
                        new PurchaseInvoice(
                                merchantId, supplierGstin, supplierName, invoiceNumber, invoiceDate,
                                taxableMinor, cgstMinor, sgstMinor, igstMinor, itcEligible));
        outbox.enqueue(merchantId, "itc.purchase.recorded", purchaseJson(invoice));
        return invoice;
    }

    /**
     * Reconciles every purchase invoice against the supplied GSTR-2B lines, updating each invoice's
     * recon status, and returns the run summary.
     */
    @Transactional
    public ReconSummary reconcileAgainst2b(UUID merchantId, List<Gstr2bLine> lines) {
        merchantScope.apply(merchantId);
        int matched = 0, mismatched = 0, missing = 0;
        for (PurchaseInvoice invoice : invoices.findByMerchantIdOrderByCreatedAtDesc(merchantId)) {
            ReconStatus status =
                    lines.stream()
                            .filter(
                                    l ->
                                            l.supplierGstin().equals(invoice.getSupplierGstin())
                                                    && l.invoiceNumber().equals(invoice.getInvoiceNumber()))
                            .findFirst()
                            .map(
                                    l ->
                                            l.totalGstMinor() == invoice.getTotalGstMinor()
                                                    ? ReconStatus.MATCHED
                                                    : ReconStatus.MISMATCHED)
                            .orElse(ReconStatus.MISSING_IN_2B);
            invoice.reconcile(status);
            switch (status) {
                case MATCHED -> matched++;
                case MISMATCHED -> mismatched++;
                case MISSING_IN_2B -> missing++;
                default -> {}
            }
        }
        ReconSummary summary = new ReconSummary(matched, mismatched, missing);
        outbox.enqueue(merchantId, "itc.reconciled", reconJson(summary));
        return summary;
    }

    /** Sums the GST of invoices that are both itc_eligible and MATCHED — the claimable ITC. */
    @Transactional(readOnly = true)
    public EligibleItcSummary eligibleItcSummary(UUID merchantId) {
        merchantScope.apply(merchantId);
        int count = 0;
        long eligibleMinor = 0;
        for (PurchaseInvoice invoice : invoices.findByMerchantIdOrderByCreatedAtDesc(merchantId)) {
            if (invoice.isItcEligible() && invoice.getReconStatus() == ReconStatus.MATCHED) {
                count++;
                eligibleMinor += invoice.getTotalGstMinor();
            }
        }
        return new EligibleItcSummary(count, eligibleMinor);
    }

    @Transactional(readOnly = true)
    public List<PurchaseInvoice> listPurchases(UUID merchantId) {
        merchantScope.apply(merchantId);
        return invoices.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    @Transactional(readOnly = true)
    public PurchaseInvoice getPurchase(UUID merchantId, UUID id) {
        merchantScope.apply(merchantId);
        return load(merchantId, id);
    }

    private PurchaseInvoice load(UUID merchantId, UUID id) {
        return invoices
                .findById(id)
                .filter(i -> i.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PurchaseInvoiceNotFoundException("no purchase invoice " + id));
    }

    /** Outcome of a GSTR-2B reconciliation run. */
    public record ReconSummary(int matched, int mismatched, int missingIn2b) {}

    /** The claimable ITC: count and summed GST of MATCHED + eligible invoices. */
    public record EligibleItcSummary(int eligibleInvoiceCount, long eligibleItcMinor) {}

    private String purchaseJson(PurchaseInvoice i) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("purchaseInvoiceId", i.getId().toString());
        b.put("supplierGstin", i.getSupplierGstin());
        b.put("invoiceNumber", i.getInvoiceNumber());
        b.put("totalGstMinor", i.getTotalGstMinor());
        b.put("itcEligible", i.isItcEligible());
        b.put("reconStatus", i.getReconStatus().name());
        return toJson(b, "failed to serialise ITC purchase event");
    }

    private String reconJson(ReconSummary s) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("matched", s.matched());
        b.put("mismatched", s.mismatched());
        b.put("missingIn2b", s.missingIn2b());
        return toJson(b, "failed to serialise ITC reconciliation event");
    }

    private String toJson(Map<String, Object> body, String failureMessage) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(failureMessage, e);
        }
    }
}
