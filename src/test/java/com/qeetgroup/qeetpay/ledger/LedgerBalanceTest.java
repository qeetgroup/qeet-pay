package com.qeetgroup.qeetpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * The ledger's core guarantee (TAD §7.1): a balanced entry posts and the books reflect it; an
 * unbalanced entry is rejected (debits must equal credits).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LedgerBalanceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired LedgerService ledger;

    @Test
    void balancedEntryPostsAndUpdatesBalances() {
        UUID merchantId = merchants.create("acme-" + slug(), "Acme").merchant().getId();
        UUID settlement = accountId(merchantId, "settlement");
        UUID revenue = accountId(merchantId, "revenue");

        UUID entryId =
                ledger.postEntry(
                        merchantId,
                        "card sale ₹4999",
                        "INR",
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, 499900),
                                new LedgerLineInput(revenue, Direction.CREDIT, 499900)));

        assertThat(entryId).isNotNull();
        // Natural (normal-side) balances: settlement is debit-normal, revenue is credit-normal.
        assertThat(ledger.balanceMinor(merchantId, settlement)).isEqualTo(499900);
        assertThat(ledger.balanceMinor(merchantId, revenue)).isEqualTo(499900);
    }

    @Test
    void unbalancedEntryIsRejected() {
        UUID merchantId = merchants.create("bad-" + slug(), "Bad Co").merchant().getId();
        UUID settlement = accountId(merchantId, "settlement");
        UUID revenue = accountId(merchantId, "revenue");

        assertThatThrownBy(
                        () ->
                                ledger.postEntry(
                                        merchantId,
                                        "lopsided",
                                        "INR",
                                        List.of(
                                                new LedgerLineInput(settlement, Direction.DEBIT, 100000),
                                                new LedgerLineInput(revenue, Direction.CREDIT, 50000))))
                .isInstanceOf(LedgerImbalanceException.class);
    }

    private UUID accountId(UUID merchantId, String code) {
        return ledger.accountsOf(merchantId).stream()
                .filter(a -> a.getCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static String slug() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
