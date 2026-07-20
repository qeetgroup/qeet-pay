package com.qeetgroup.qeetpay.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Offline &amp; Rural rails (PRD Module 15), all simulated. Covers: Bharat QR payload generation
 * (static + dynamic); UPI Lite top-up/spend posting to the ledger, plus per-transaction (₹500) and
 * per-day (₹2,000) limit enforcement; and a POS capture posting money-in to the ledger.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class OfflineFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired BharatQrService bharatQr;
    @Autowired UpiLiteService upiLite;
    @Autowired Pay123Service pay123;
    @Autowired PosService pos;
    @Autowired LedgerService ledger;

    @Test
    void generatesUnifiedBharatQrForUpiAndCardNetworks() {
        UUID merchantId = newMerchant();

        BharatQr dynamic = bharatQr.generate(merchantId, 10000L, "INR", "Rural Kirana", "order_1");
        assertThat(dynamic.isDynamic()).isTrue();
        assertThat(dynamic.getPayload())
                .contains("upi://pay")
                .contains("RUPAY")
                .contains("VISA")
                .contains("MASTERCARD")
                .contains("am=100.00"); // 10000 paise -> ₹100.00

        BharatQr openAmount = bharatQr.generate(merchantId, null, null, null, null);
        assertThat(openAmount.isDynamic()).isFalse();
        assertThat(openAmount.getPayload()).doesNotContain("am=");
        assertThat(openAmount.getCurrency()).isEqualTo("INR"); // defaulted

        assertThat(bharatQr.list(merchantId)).hasSize(2);
    }

    @Test
    void upiLiteTopUpAndSpendPostToLedgerAndEnforcePerTxnLimit() {
        UUID merchantId = newMerchant();
        UpiLiteWallet wallet = upiLite.createWallet(merchantId, "cust_lite_1", "INR");

        // Top-up loads value: debit settlement / credit liability.
        upiLite.topUp(merchantId, wallet.getId(), 100000L); // ₹1,000
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(100000L);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "liability"))).isEqualTo(100000L);

        // A spend one paise over the ₹500 per-transaction cap is rejected before any posting.
        assertThatThrownBy(() -> upiLite.spend(merchantId, wallet.getId(), 50001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-transaction");

        // A spend at the limit succeeds: debit liability / credit revenue.
        UpiLiteTxn spend = upiLite.spend(merchantId, wallet.getId(), 50000L);
        assertThat(spend.getType()).isEqualTo(UpiLiteTxnType.SPEND);
        assertThat(spend.getLedgerEntryId()).isNotNull();
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(50000L);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "liability"))).isEqualTo(50000L);
        assertThat(upiLite.getWallet(merchantId, wallet.getId()).wallet().getBalanceMinor())
                .isEqualTo(50000L);
    }

    @Test
    void upiLiteEnforcesPerDayLimit() {
        UUID merchantId = newMerchant();
        UpiLiteWallet wallet = upiLite.createWallet(merchantId, "cust_lite_2", "INR");
        upiLite.topUp(merchantId, wallet.getId(), 300000L); // ₹3,000 balance

        // Four ₹500 spends reach exactly the ₹2,000 daily cap.
        for (int i = 0; i < 4; i++) {
            upiLite.spend(merchantId, wallet.getId(), 50000L);
        }
        assertThat(upiLite.getWallet(merchantId, wallet.getId()).wallet().getBalanceMinor())
                .isEqualTo(100000L);

        // A further spend — even ₹1 — breaches the per-day cap and is rejected (balance unchanged).
        assertThatThrownBy(() -> upiLite.spend(merchantId, wallet.getId(), 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-day");
        assertThat(upiLite.getWallet(merchantId, wallet.getId()).wallet().getBalanceMinor())
                .isEqualTo(100000L);
    }

    @Test
    void posCapturePostsMoneyInToTheLedger() {
        UUID merchantId = newMerchant();
        PosDevice device = pos.registerDevice(merchantId, "Counter 1", "SN-0001");
        assertThat(device.isActive()).isTrue();

        PosTransaction txn =
                pos.capture(merchantId, device.getId(), 75000L, "INR", PosCaptureMethod.TAP);
        assertThat(txn.getLedgerEntryId()).isNotNull();
        assertThat(txn.getRrn()).isNotBlank();

        // Money-in: settlement (asset) and revenue both up by the captured amount.
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(75000L);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(75000L);
    }

    @Test
    void pay123IntentConfirmPostsMoneyIn() {
        UUID merchantId = newMerchant();
        Pay123Intent intent = pay123.createIntent(merchantId, "+919876500000", 42000L, "INR");
        assertThat(intent.getStatus()).isEqualTo(Pay123Status.CREATED);
        assertThat(intent.getLedgerEntryId()).isNull();

        Pay123Intent confirmed = pay123.confirmIntent(merchantId, intent.getId());
        assertThat(confirmed.getStatus()).isEqualTo(Pay123Status.CONFIRMED);
        assertThat(confirmed.getLedgerEntryId()).isNotNull();
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "settlement"))).isEqualTo(42000L);
        assertThat(ledger.balanceMinor(merchantId, account(merchantId, "revenue"))).isEqualTo(42000L);

        // Re-confirming an already-confirmed intent is rejected.
        assertThatThrownBy(() -> pay123.confirmIntent(merchantId, intent.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants
                .create("offline-" + UUID.randomUUID().toString().substring(0, 8), "Offline Co")
                .merchant()
                .getId();
    }

    private UUID account(UUID merchantId, String code) {
        return ledger.accountByCode(merchantId, code).getId();
    }
}
