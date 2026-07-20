package com.qeetgroup.qeetpay.gst;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Regulatory-Change Impact Radar (PRD Module 06.5). Verifies that recording an announced rate change and
 * computing its impact identifies exactly the merchant's invoices carrying the affected HSN/SAC, and
 * forecasts the tax delta at the new rate — labelled as a forecast with a confidence.
 */
class RegChangeRadarTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired GstInvoiceService gst;
    @Autowired RegChangeRadar radar;

    @Test
    void impactReportIdentifiesAffectedInvoicesAndForecastsDelta() {
        UUID merchantId = newMerchant();

        // Two invoices carry HSN 8471 (laptops) @ 18% intra-state; one carries HSN 9403 (furniture).
        GstInvoice a = invoice(merchantId, "8471", 100_000);
        GstInvoice b = invoice(merchantId, "8471", 100_000);
        invoice(merchantId, "9403", 100_000); // unaffected

        RegulatoryChange change =
                radar.recordChange(
                        merchantId, "8471", RegChangeType.RATE_CHANGE, 18, 28,
                        LocalDate.now().plusMonths(1), "Laptops 18% → 28%", "GST Council");

        RegChangeImpactReport report = radar.computeImpact(merchantId, change.getId(), Set.of());

        assertThat(report.forecast()).isTrue();
        assertThat(report.confidence()).isGreaterThan(0.0);
        assertThat(report.decisionId()).isNotBlank();
        assertThat(report.hsnSac()).isEqualTo("8471");
        assertThat(report.newRatePct()).isEqualTo(28);

        assertThat(report.affectedInvoiceCount()).isEqualTo(2);
        assertThat(report.affectedLineCount()).isEqualTo(2);
        assertThat(report.totalTaxableMinor()).isEqualTo(200_000);
        assertThat(report.currentGstMinor()).isEqualTo(36_000);   // 18% of 200_000, split CGST+SGST
        assertThat(report.forecastGstMinor()).isEqualTo(56_000);  // 28% of 200_000
        assertThat(report.deltaGstMinor()).isEqualTo(20_000);

        assertThat(report.invoices()).hasSize(2);
        assertThat(report.invoices())
                .extracting(RegChangeImpactReport.InvoiceImpact::invoiceNumber)
                .containsExactlyInAnyOrder(a.getInvoiceNumber(), b.getInvoiceNumber());
    }

    @Test
    void unaffectedHsnYieldsZeroExposure() {
        UUID merchantId = newMerchant();
        invoice(merchantId, "8471", 100_000);

        RegulatoryChange change =
                radar.recordChange(
                        merchantId, "9999", RegChangeType.RATE_CHANGE, 5, 12,
                        LocalDate.now().plusMonths(2), "Some other HSN", "GST Council");

        RegChangeImpactReport report = radar.computeImpact(merchantId, change.getId(), Set.of());

        assertThat(report.affectedInvoiceCount()).isZero();
        assertThat(report.deltaGstMinor()).isZero();
        assertThat(report.invoices()).isEmpty();
        assertThat(report.forecast()).isTrue();
    }

    private GstInvoice invoice(UUID merchantId, String hsn, long unitPriceMinor) {
        return gst.createInvoice(
                        merchantId, "27ABCDE1234F1Z5", "27AAAAA0000A1Z5", "27", "INR",
                        List.of(new GstLineInput("item " + hsn, hsn, 1, unitPriceMinor, 18)))
                .invoice();
    }

    private UUID newMerchant() {
        return merchants.create("radar-" + UUID.randomUUID().toString().substring(0, 8), "Radar Co")
                .merchant()
                .getId();
    }
}
