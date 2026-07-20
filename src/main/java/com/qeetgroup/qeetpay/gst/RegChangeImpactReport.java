package com.qeetgroup.qeetpay.gst;

import java.time.LocalDate;
import java.util.List;

/**
 * The forecast impact of a {@link RegulatoryChange} over a merchant's existing GST invoices (PRD Module
 * 06.5). All money is minor units (paise). This is explicitly a <b>forecast</b> ({@code forecast=true}
 * with a {@code confidence} and {@code disclaimer}) — it estimates the tax delta if the change applied
 * to the merchant's current supply mix for the affected HSN/SAC; it is not tax advice.
 *
 * @param changeId the regulatory change this report is for
 * @param hsnSac the affected HSN/SAC code
 * @param oldRatePct the pre-change rate (as announced, may be null)
 * @param newRatePct the announced new rate
 * @param effectiveDate when the change takes effect
 * @param forecast always {@code true} — this output is a projection, not a settled figure
 * @param confidence forecast confidence in {@code [0,1]} (grows with the merchant's exposure data)
 * @param disclaimer plain-language caveat that this is an estimate
 * @param affectedInvoiceCount distinct invoices carrying a line with this HSN/SAC
 * @param affectedLineCount invoice lines carrying this HSN/SAC
 * @param totalTaxableMinor total taxable value across affected lines
 * @param currentGstMinor GST currently computed on affected lines
 * @param forecastGstMinor GST those lines would carry at {@code newRatePct}
 * @param deltaGstMinor {@code forecastGstMinor - currentGstMinor} (positive = more tax)
 * @param decisionId the AiGateway audit-row id for this forecast
 * @param invoices per-invoice breakdown
 */
public record RegChangeImpactReport(
        String changeId,
        String hsnSac,
        Integer oldRatePct,
        int newRatePct,
        LocalDate effectiveDate,
        boolean forecast,
        double confidence,
        String disclaimer,
        int affectedInvoiceCount,
        int affectedLineCount,
        long totalTaxableMinor,
        long currentGstMinor,
        long forecastGstMinor,
        long deltaGstMinor,
        String decisionId,
        List<InvoiceImpact> invoices) {

    /** Per-invoice forecast line in an impact report. */
    public record InvoiceImpact(
            String invoiceId,
            String invoiceNumber,
            long taxableMinor,
            long currentGstMinor,
            long forecastGstMinor,
            long deltaGstMinor) {}
}
