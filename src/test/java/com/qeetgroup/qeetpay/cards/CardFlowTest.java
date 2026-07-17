package com.qeetgroup.qeetpay.cards;

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
 * Virtual-card flow (PRD Module 10): loading a wallet card moves cash into card_liability; spending
 * reverses part of it and the ledger balance tracks the card balance one-for-one. Over-spending is
 * rejected, and a frozen card cannot be spent.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CardFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired CardService cards;
    @Autowired LedgerService ledger;

    @Test
    void loadThenSpendTracksCardLiability() {
        UUID merchantId = newMerchant();

        VirtualCard card = cards.issueCard(merchantId, "holder-1", CardType.WALLET, "INR");
        UUID cardId = card.getId();
        assertThat(card.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.getBalanceMinor()).isZero();
        assertThat(card.getMaskedPan()).startsWith("XXXX XXXX XXXX ");

        // Load ₹1,000 onto the card: settlement debited, card_liability credited.
        VirtualCard loaded = cards.loadCard(merchantId, cardId, 100_000L);
        assertThat(loaded.getBalanceMinor()).isEqualTo(100_000L);
        assertThat(cardLiability(merchantId)).isEqualTo(100_000L);

        // Spend ₹300 from the card.
        VirtualCard spent = cards.spend(merchantId, cardId, 30_000L, "office lunch");
        assertThat(spent.getBalanceMinor()).isEqualTo(70_000L);
        assertThat(cardLiability(merchantId)).isEqualTo(70_000L);
        assertThat(cards.getCard(merchantId, cardId).transactions()).hasSize(2); // LOAD + SPEND
    }

    @Test
    void cannotOverspend() {
        UUID merchantId = newMerchant();
        UUID cardId = cards.issueCard(merchantId, "holder-2", CardType.WALLET, "INR").getId();
        cards.loadCard(merchantId, cardId, 100_000L);
        assertThatThrownBy(() -> cards.spend(merchantId, cardId, 999_999L, "too much"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotSpendFrozenCard() {
        UUID merchantId = newMerchant();
        UUID cardId = cards.issueCard(merchantId, "holder-3", CardType.EXPENSE, "INR").getId();
        cards.loadCard(merchantId, cardId, 100_000L);
        cards.freeze(merchantId, cardId);
        assertThatThrownBy(() -> cards.spend(merchantId, cardId, 30_000L, "blocked"))
                .isInstanceOf(IllegalStateException.class);
    }

    private UUID newMerchant() {
        return merchants.create("card-" + UUID.randomUUID().toString().substring(0, 8), "Card Co")
                .merchant().getId();
    }

    private long cardLiability(UUID merchantId) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, "card_liability").getId());
    }
}
