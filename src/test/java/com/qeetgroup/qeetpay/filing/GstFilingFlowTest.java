package com.qeetgroup.qeetpay.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.gst.GstInvoiceService;
import com.qeetgroup.qeetpay.gst.GstLineInput;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * GST return filing flow (TAD §7.4): preparing a GSTR-1 aggregates the current period's invoices into
 * a return with per-invoice lines and matching CGST/SGST totals; filing it yields a 15-char ARN and
 * makes the return immutable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GstFilingFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired GstInvoiceService gst;
    @Autowired FilingService filing;

    @Test
    void preparesAndFilesGstr1ForTheCurrentPeriod() {
        UUID merchantId = newMerchant();
        // Two intra-state invoices this period: 18% GST on ₹1000 and ₹2000 taxable.
        gst.createInvoice(merchantId, "27ABCDE1234F1Z5", "27AAAAA0000A1Z5", "27", "INR",
                List.of(new GstLineInput("Item A", "998314", 1, 100_000, 18)));
        gst.createInvoice(merchantId, "27ABCDE1234F1Z5", null, "27", "INR",
                List.of(new GstLineInput("Item B", "998314", 1, 200_000, 18)));

        String period = YearMonth.now().toString(); // YYYY-MM

        FilingService.ReturnWithLines prepared =
                filing.prepareReturn(merchantId, GstReturnType.GSTR1, period);
        assertThat(prepared.ret().getStatus()).isEqualTo(GstReturnStatus.PREPARED);
        assertThat(prepared.ret().getInvoiceCount()).isEqualTo(2);
        assertThat(prepared.lines()).hasSize(2);
        assertThat(prepared.ret().getTotalTaxableMinor()).isEqualTo(300_000);
        assertThat(prepared.ret().getTotalCgstMinor()).isEqualTo(27_000); // 9% of 300000
        assertThat(prepared.ret().getTotalSgstMinor()).isEqualTo(27_000);
        assertThat(prepared.ret().getTotalIgstMinor()).isZero();
        assertThat(prepared.ret().getTotalTaxMinor()).isEqualTo(54_000);

        GstReturn filed = filing.fileReturn(merchantId, prepared.ret().getId());
        assertThat(filed.getStatus()).isEqualTo(GstReturnStatus.FILED);
        assertThat(filed.getGstnArn()).startsWith("AA").hasSize(15);
        assertThat(filed.getFiledAt()).isNotNull();

        // Filing again is a no-op with the same ARN.
        assertThat(filing.fileReturn(merchantId, prepared.ret().getId()).getGstnArn())
                .isEqualTo(filed.getGstnArn());
    }

    @Test
    void reprepareReplacesLinesUntilFiled() {
        UUID merchantId = newMerchant();
        gst.createInvoice(merchantId, "27ABCDE1234F1Z5", null, "27", "INR",
                List.of(new GstLineInput("Only item", "998314", 1, 100_000, 18)));
        String period = YearMonth.now().toString();

        UUID returnId = filing.prepareReturn(merchantId, GstReturnType.GSTR1, period).ret().getId();
        // add another invoice, re-prepare: the same return now reflects both.
        gst.createInvoice(merchantId, "27ABCDE1234F1Z5", null, "27", "INR",
                List.of(new GstLineInput("Second item", "998314", 1, 100_000, 18)));
        FilingService.ReturnWithLines reprepared =
                filing.prepareReturn(merchantId, GstReturnType.GSTR1, period);

        assertThat(reprepared.ret().getId()).isEqualTo(returnId); // same row, not a duplicate
        assertThat(reprepared.lines()).hasSize(2);

        filing.fileReturn(merchantId, returnId);
        assertThatThrownBy(() -> filing.prepareReturn(merchantId, GstReturnType.GSTR1, period))
                .isInstanceOf(IllegalStateException.class); // cannot re-prepare a filed return
    }

    @Test
    void gstr3bIsSummaryOnly() {
        UUID merchantId = newMerchant();
        gst.createInvoice(merchantId, "27ABCDE1234F1Z5", "29BBBBB0000B1Z5", "29", "INR",
                List.of(new GstLineInput("Inter-state", "998314", 1, 100_000, 18)));
        String period = YearMonth.now().toString();

        FilingService.ReturnWithLines summary =
                filing.prepareReturn(merchantId, GstReturnType.GSTR3B, period);
        assertThat(summary.lines()).isEmpty(); // no per-invoice detail for 3B
        assertThat(summary.ret().getTotalIgstMinor()).isEqualTo(18_000); // inter-state => IGST
        assertThat(summary.ret().getTotalCgstMinor()).isZero();
    }

    @Test
    void rejectsMalformedPeriod() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() -> filing.prepareReturn(merchantId, GstReturnType.GSTR1, "2026/07"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UUID newMerchant() {
        return merchants.create("fil-" + UUID.randomUUID().toString().substring(0, 8), "Filing Co")
                .merchant().getId();
    }
}
