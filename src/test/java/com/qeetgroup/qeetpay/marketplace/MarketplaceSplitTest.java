package com.qeetgroup.qeetpay.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
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
 * Marketplace split flow (TAD §5 "Marketplace"): a collected gross is reclassified into commission
 * revenue, tax payable (commission GST + TCS + TDS), and seller payables — and cancelling a split
 * posts the exact offsetting entry so every account nets back to zero.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MarketplaceSplitTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired MarketplaceService marketplace;
    @Autowired LedgerService ledger;

    @Test
    void splitsAcrossTwoSellersAndPostsBalancedEntry() {
        UUID merchantId = newMerchant();
        marketplace.registerSeller(merchantId, "seller-a", "Seller A", "27AAAAA0000A1Z5", "AAAAA0000A", 500);
        marketplace.registerSeller(merchantId, "seller-b", "Seller B", "29BBBBB0000B1Z5", "BBBBB0000B", 500);

        MarketplaceService.SplitWithItems split =
                marketplace.createSplit(
                        merchantId, null, "order_1", "INR",
                        List.of(
                                new SplitLineInput("seller-a", 100_000, null, null, null, null),
                                new SplitLineInput("seller-b", 100_000, null, null, null, null)));

        assertThat(split.items()).hasSize(2);
        assertThat(split.split().getGrossMinor()).isEqualTo(200_000);
        assertThat(split.split().getCommissionMinor()).isEqualTo(10_000);
        assertThat(split.split().getCommissionGstMinor()).isEqualTo(1_800);
        assertThat(split.split().getTcsMinor()).isEqualTo(2_000);
        assertThat(split.split().getTdsMinor()).isEqualTo(2_000);
        assertThat(split.split().getSellerNetMinor()).isEqualTo(184_200);

        // settlement debited by gross; commission→revenue; (gst+tcs+tds)→tax_payable; net→seller liability.
        assertThat(balance(merchantId, "settlement")).isEqualTo(200_000);
        assertThat(balance(merchantId, "revenue")).isEqualTo(10_000);
        assertThat(balance(merchantId, "tax_payable")).isEqualTo(5_800);
        assertThat(balance(merchantId, "liability")).isEqualTo(184_200);
    }

    @Test
    void cancellingASplitOffsetsEveryAccount() {
        UUID merchantId = newMerchant();
        marketplace.registerSeller(merchantId, "seller-x", "Seller X", null, null, 300);
        MarketplaceService.SplitWithItems split =
                marketplace.createSplit(
                        merchantId, null, "order_9", "INR",
                        List.of(new SplitLineInput("seller-x", 100_000, null, null, null, null)));

        SplitPayment cancelled = marketplace.cancelSplit(merchantId, split.split().getId());
        assertThat(cancelled.getStatus()).isEqualTo(SplitStatus.CANCELLED);
        assertThat(cancelled.getReversalEntryId()).isNotNull();

        assertThat(balance(merchantId, "settlement")).isZero();
        assertThat(balance(merchantId, "revenue")).isZero();
        assertThat(balance(merchantId, "tax_payable")).isZero();
        assertThat(balance(merchantId, "liability")).isZero();

        // Cancelling again is a no-op.
        assertThat(marketplace.cancelSplit(merchantId, split.split().getId()).getStatus())
                .isEqualTo(SplitStatus.CANCELLED);
    }

    @Test
    void suspendedSellerCannotBeSplit() {
        UUID merchantId = newMerchant();
        MarketplaceSeller seller =
                marketplace.registerSeller(merchantId, "seller-s", "Seller S", null, null, 200);
        marketplace.setSellerStatus(merchantId, seller.getId(), false);

        assertThatThrownBy(() ->
                        marketplace.createSplit(
                                merchantId, null, "order_s", "INR",
                                List.of(new SplitLineInput("seller-s", 50_000, null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("mkt-" + UUID.randomUUID().toString().substring(0, 8), "Marketplace Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
