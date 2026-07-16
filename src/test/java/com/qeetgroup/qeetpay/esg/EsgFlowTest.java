package com.qeetgroup.qeetpay.esg;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * ESG carbon-footprint flow (PRD Module 16): recording payments' footprints aggregates into the
 * summary without touching the ledger; buying an offset posts a balanced entry (debit fees / credit
 * settlement) for its cost and nets down the recorded footprint. A zero-cost offset skips the ledger.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EsgFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired EsgService esg;
    @Autowired LedgerService ledger;

    @Test
    void recordsFootprintsThenOffsetsPostToLedger() {
        UUID merchantId = newMerchant();

        esg.recordFootprint(merchantId, "txn-1", CarbonMethod.UPI, 100_000L);
        esg.recordFootprint(merchantId, "txn-2", CarbonMethod.CARD, 250_000L);
        esg.recordFootprint(merchantId, "txn-3", CarbonMethod.NET_BANKING, 50_000L);
        esg.recordFootprint(merchantId, "txn-4", CarbonMethod.WALLET, 100_000L);

        // Recording never touches the ledger.
        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "fees")).isZero();

        long expectedGrams =
                CarbonCalculator.gramsCo2(CarbonMethod.UPI, 100_000L)
                        + CarbonCalculator.gramsCo2(CarbonMethod.CARD, 250_000L)
                        + CarbonCalculator.gramsCo2(CarbonMethod.NET_BANKING, 50_000L)
                        + CarbonCalculator.gramsCo2(CarbonMethod.WALLET, 100_000L);

        EsgService.FootprintSummary summary = esg.footprintSummary(merchantId);
        assertThat(summary.recordCount()).isEqualTo(4);
        assertThat(summary.totalGramsCo2()).isEqualTo(expectedGrams);
        assertThat(summary.totalGramsOffset()).isZero();
        assertThat(summary.netGramsCo2()).isEqualTo(expectedGrams);
        assertThat(esg.listRecords(merchantId)).hasSize(4);

        // Neutralise the recorded footprint at ₹5000/tonne → a positive cost posts to the ledger.
        long gramsToOffset = summary.totalGramsCo2();
        long pricePerTonneMinor = 500_000L;
        long expectedCost = CarbonCalculator.offsetCostMinor(gramsToOffset, pricePerTonneMinor);
        assertThat(expectedCost).isPositive();

        CarbonOffset offset = esg.purchaseOffset(merchantId, gramsToOffset, "INR", pricePerTonneMinor);
        assertThat(offset.getCostMinor()).isEqualTo(expectedCost);
        assertThat(offset.getLedgerEntryId()).isNotNull();

        // Offset cost: fees debited (+cost), settlement credited (−cost); the entry balances.
        assertThat(balance(merchantId, "fees")).isEqualTo(expectedCost);
        assertThat(balance(merchantId, "settlement")).isEqualTo(-expectedCost);

        EsgService.FootprintSummary after = esg.footprintSummary(merchantId);
        assertThat(after.totalGramsOffset()).isEqualTo(gramsToOffset);
        assertThat(after.netGramsCo2()).isEqualTo(expectedGrams - gramsToOffset);
    }

    @Test
    void zeroCostOffsetIsRecordedWithoutLedgerEntry() {
        UUID merchantId = newMerchant();

        CarbonOffset offset = esg.purchaseOffset(merchantId, 5_000L, "INR", 0L);
        assertThat(offset.getCostMinor()).isZero();
        assertThat(offset.getLedgerEntryId()).isNull();
        assertThat(offset.getNote()).isNotBlank();

        // No cost → the ledger is untouched, but the grams still count toward the summary.
        assertThat(balance(merchantId, "fees")).isZero();
        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(esg.footprintSummary(merchantId).totalGramsOffset()).isEqualTo(5_000L);
    }

    private UUID newMerchant() {
        return merchants.create("esg-" + UUID.randomUUID().toString().substring(0, 8), "ESG Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
