package com.qeetgroup.qeetpay.virtualaccounts;

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
 * Virtual-accounts flow (PRD Module 01): minting is idempotent per active customer, an inbound credit
 * auto-reconciles to the ledger (debit settlement / credit revenue), a replayed UTR does not
 * double-credit, and a closed account rejects further credits.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class VirtualAccountFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired VirtualAccountService va;
    @Autowired LedgerService ledger;

    @Test
    void mintIsIdempotentPerActiveCustomer() {
        UUID merchantId = newMerchant();
        VirtualAccount first = va.mintAccount(merchantId, "cust-1");
        VirtualAccount again = va.mintAccount(merchantId, "cust-1");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(first.getVaNumber()).startsWith("QV");
        assertThat(first.getIfsc()).isEqualTo("QEET0000001");
        assertThat(first.isActive()).isTrue();
    }

    @Test
    void inboundCreditAutoReconcilesToLedgerAndIsIdempotent() {
        UUID merchantId = newMerchant();
        VirtualAccount account = va.mintAccount(merchantId, "cust-2");

        va.ingestCredit(merchantId, account.getId(), 250_000L, "INR", "UTR12345", "Acme Buyer", "acme@upi");
        assertThat(balance(merchantId, "settlement")).isEqualTo(250_000L);
        assertThat(balance(merchantId, "revenue")).isEqualTo(250_000L);

        // Replaying the same UTR must not double-credit.
        VirtualAccountCredit replay =
                va.ingestCredit(merchantId, account.getId(), 250_000L, "INR", "UTR12345", "Acme Buyer", "acme@upi");
        assertThat(replay.getUtr()).isEqualTo("UTR12345");
        assertThat(balance(merchantId, "settlement")).isEqualTo(250_000L); // unchanged

        // A second, distinct credit adds to the balance.
        va.ingestCredit(merchantId, account.getId(), 100_000L, "INR", "UTR67890", "Beta Buyer", "beta@upi");
        assertThat(balance(merchantId, "settlement")).isEqualTo(350_000L);
        assertThat(va.getAccount(merchantId, account.getId()).credits()).hasSize(2);
    }

    @Test
    void closedAccountRejectsCredits() {
        UUID merchantId = newMerchant();
        VirtualAccount account = va.mintAccount(merchantId, "cust-3");
        va.closeAccount(merchantId, account.getId());

        assertThatThrownBy(() ->
                        va.ingestCredit(merchantId, account.getId(), 100_000L, "INR", "UTR-X", null, null))
                .isInstanceOf(IllegalStateException.class);

        // Re-minting after close yields a fresh active account.
        VirtualAccount reminted = va.mintAccount(merchantId, "cust-3");
        assertThat(reminted.getId()).isNotEqualTo(account.getId());
        assertThat(reminted.isActive()).isTrue();
    }

    private UUID newMerchant() {
        return merchants.create("va-" + UUID.randomUUID().toString().substring(0, 8), "VA Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
