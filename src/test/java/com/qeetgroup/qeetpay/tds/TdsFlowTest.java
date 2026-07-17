package com.qeetgroup.qeetpay.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
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
 * TDS/TCS tracking flow (PRD Module 06): recording a deduction computes the tax at source and derives
 * its FY quarter; issuing a certificate stamps a deterministic number (once only); the quarterly
 * summary aggregates a quarter's deductions with a per-section tax breakdown.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TdsFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired TdsService tds;

    @Test
    void recordsDeductionComputesTaxAndDerivesQuarter() {
        UUID merchantId = newMerchant();
        // 194J professional fees: ₹1000 gross at 10% (1000 bps) => ₹100 TDS, deducted 2026-07-15 (Q2).
        TdsDeduction d =
                tds.recordDeduction(
                        merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                        100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15));

        assertThat(d.getTaxMinor()).isEqualTo(10_000);
        assertThat(d.getQuarter()).isEqualTo("2026-Q2");
        assertThat(d.getCertificateNo()).isNull();
    }

    @Test
    void issuesCertificateOnceOnly() {
        UUID merchantId = newMerchant();
        TdsDeduction d =
                tds.recordDeduction(
                        merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                        100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15));

        TdsDeduction certified = tds.issueCertificate(merchantId, d.getId());
        assertThat(certified.getCertificateNo()).isNotBlank().startsWith("QP/TDS/194J/");

        // A second issuance is rejected (the certificate is immutable once set).
        assertThatThrownBy(() -> tds.issueCertificate(merchantId, d.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void quarterlySummaryAggregatesTotalsAndPerSectionTax() {
        UUID merchantId = newMerchant();
        // Two deductions in the same FY quarter (2026-Q2), different sections.
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194J", "Acme Consultants", "AAAPA1234C",
                100_000, 1000, "txn-1", LocalDate.of(2026, 7, 15)); // ₹100 tax
        tds.recordDeduction(
                merchantId, TaxKind.TDS, "194C", "Build Co", "BBBPB5678D",
                50_000, 200, "txn-2", LocalDate.of(2026, 8, 10)); // ₹10 tax

        TdsService.QuarterlySummary summary = tds.quarterlySummary(merchantId, "2026-Q2");

        assertThat(summary.quarter()).isEqualTo("2026-Q2");
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalGrossMinor()).isEqualTo(150_000);
        assertThat(summary.totalTaxMinor()).isEqualTo(11_000);
        assertThat(summary.taxBySection())
                .containsEntry("194J", 10_000L)
                .containsEntry("194C", 1_000L)
                .hasSize(2);
    }

    @Test
    void recordsTcsUnderSection52() {
        UUID merchantId = newMerchant();
        TdsDeduction d =
                tds.recordDeduction(
                        merchantId, TaxKind.TCS, "52", "Marketplace Seller", "CCCPC9012E",
                        200_000, 100, "order-9", LocalDate.of(2027, 1, 20));

        assertThat(d.getKind()).isEqualTo(TaxKind.TCS);
        assertThat(d.getTaxMinor()).isEqualTo(2_000); // 1% of ₹2000
        assertThat(d.getQuarter()).isEqualTo("2026-Q4"); // Jan => prior FY's Q4
    }

    private UUID newMerchant() {
        return merchants.create("tds-" + UUID.randomUUID().toString().substring(0, 8), "TDS Co")
                .merchant().getId();
    }
}
