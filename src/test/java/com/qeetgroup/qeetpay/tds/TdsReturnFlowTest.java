package com.qeetgroup.qeetpay.tds;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * TDS/TCS statutory-return flow (PRD Module 06.4): preparing a return aggregates only the quarter's
 * deductions that belong to the requested form; exporting renders the NSDL FVU-style detail columns;
 * filing assigns an acknowledgement token (idempotently). These exercise {@link TdsReturnService} on
 * top of the deductions recorded by {@link TdsService}; the existing {@link TdsCalculator} behaviour is
 * untouched.
 */
class TdsReturnFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired TdsService tds;
    @Autowired TdsReturnService returns;

    @Test
    void prepare26QAggregatesOnlyNonSalaryTdsForTheQuarter() {
        UUID merchantId = newMerchant();
        // Two non-salary TDS rows in 2026-Q2 (belong on 26Q)…
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15)); // ₹100 tax
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194C", "Build Co", "BBBPB5678D",
                50_000, 200, "txn-2", LocalDate.of(2026, 8, 10)); // ₹10 tax
        // …a salary TDS row (§192 → belongs on 24Q, not 26Q)…
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "192", "Employee One", "DDDPD1111F",
                500_000, 500, "sal-1", LocalDate.of(2026, 9, 1));
        // …and a TCS row (§52 → belongs on 27EQ, not 26Q).
        tds.recordDeduction(
                merchantId, TaxKind.TCS, "52", "Marketplace Seller", "CCCPC9012E",
                200_000, 100, "order-9", LocalDate.of(2026, 8, 20));

        TdsReturnService.ReturnWithLines prepared =
                returns.prepareReturn(merchantId, TdsReturnForm.FORM_26Q, "2026-Q2");
        TdsReturn ret = prepared.ret();

        assertThat(ret.getForm()).isEqualTo(TdsReturnForm.FORM_26Q);
        assertThat(ret.getFy()).isEqualTo("2026-27");
        assertThat(ret.getQuarter()).isEqualTo("Q2");
        assertThat(ret.getStatus()).isEqualTo(TdsReturnStatus.PREPARED);
        // Only the two non-salary TDS rows are kept.
        assertThat(ret.getDeductionCount()).isEqualTo(2);
        assertThat(ret.getDeducteeCount()).isEqualTo(2);
        assertThat(ret.getTotalGrossMinor()).isEqualTo(150_000);
        assertThat(ret.getTotalTaxMinor()).isEqualTo(11_000);
        assertThat(ret.getBsrCode()).isNotBlank();
        assertThat(ret.getChallanNo()).isNotBlank();
        assertThat(prepared.lines()).hasSize(2)
                .extracting(TdsReturnLine::getSection)
                .containsExactlyInAnyOrder("194J", "194C");
    }

    @Test
    void prepare27EQPicksUpOnlyTcs() {
        UUID merchantId = newMerchant();
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15));
        tds.recordDeduction(
                merchantId, TaxKind.TCS, "52", "Marketplace Seller", "CCCPC9012E",
                200_000, 100, "order-9", LocalDate.of(2026, 8, 20)); // ₹20 TCS

        TdsReturn ret =
                returns.prepareReturn(merchantId, TdsReturnForm.FORM_27EQ, "2026-Q2").ret();

        assertThat(ret.getForm()).isEqualTo(TdsReturnForm.FORM_27EQ);
        assertThat(ret.getDeductionCount()).isEqualTo(1);
        assertThat(ret.getTotalTaxMinor()).isEqualTo(2_000);
    }

    @Test
    void exportRendersFvuDetailColumnsAndRows() {
        UUID merchantId = newMerchant();
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15));

        TdsReturn ret = returns.prepareReturn(merchantId, TdsReturnForm.FORM_26Q, "2026-Q2").ret();
        String export = returns.export(merchantId, ret.getId());

        // FVU record markers + the deductee-detail column header.
        assertThat(export).contains("FH^NSDL^e-TDS/TCS");
        assertThat(export).contains(TdsReturnExporter.columnHeader(TdsReturnForm.FORM_26Q));
        assertThat(export).contains("DeducteePAN").contains("TaxDeductedRs");
        // The one detail row, rendered in rupees (paise / 100).
        assertThat(export).contains("DD^1^AAAPA1234C^Acme Consultants^194J^1000.00^100.00^2026-07-15^10.00");
        assertThat(export).contains("FT^1^100.00");
    }

    @Test
    void fileAssignsAcknowledgementTokenIdempotently() {
        UUID merchantId = newMerchant();
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15));
        TdsReturn prepared = returns.prepareReturn(merchantId, TdsReturnForm.FORM_26Q, "2026-Q2").ret();

        TdsReturn filed = returns.fileReturn(merchantId, prepared.getId());
        assertThat(filed.getStatus()).isEqualTo(TdsReturnStatus.FILED);
        assertThat(filed.getAckToken()).isNotBlank().hasSize(15).containsPattern("\\d{15}");
        assertThat(filed.getFiledAt()).isNotNull();

        // Filing again is idempotent — same token, still FILED.
        TdsReturn refiled = returns.fileReturn(merchantId, prepared.getId());
        assertThat(refiled.getStatus()).isEqualTo(TdsReturnStatus.FILED);
        assertThat(refiled.getAckToken()).isEqualTo(filed.getAckToken());
    }

    private UUID newMerchant() {
        return merchants.create("tds-" + UUID.randomUUID().toString().substring(0, 8), "TDS Co")
                .merchant().getId();
    }
}
