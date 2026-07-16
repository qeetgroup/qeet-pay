package com.qeetgroup.qeetpay.cards;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Virtual-cards API (PRD Module 10): issue an expense/wallet card, load funds onto it, spend from it,
 * freeze/unfreeze/close it, and read cards with their transaction history.
 */
@Tag(
        name = "Cards",
        description = "Virtual expense/wallet cards — issue, load funds, spend, freeze/unfreeze/close, and read transaction history.")
@RestController
@RequestMapping("/v1/cards")
public class CardController {

    private final CardService cards;

    public CardController(CardService cards) {
        this.cards = cards;
    }

    @PostMapping
    public ResponseEntity<CardView> issue(@Valid @RequestBody IssueRequest req) {
        UUID merchantId = MerchantContext.require();
        VirtualCard card = cards.issueCard(merchantId, req.holderRef(), req.type(), req.currency());
        return ResponseEntity.status(HttpStatus.CREATED).body(CardView.of(cards.getCard(merchantId, card.getId())));
    }

    @GetMapping
    public List<CardSummary> list() {
        return cards.listCards(MerchantContext.require()).stream().map(CardSummary::of).toList();
    }

    @GetMapping("/{cardId}")
    public CardView get(@PathVariable UUID cardId) {
        return CardView.of(cards.getCard(MerchantContext.require(), cardId));
    }

    @PostMapping("/{cardId}/load")
    public CardView load(@PathVariable UUID cardId, @Valid @RequestBody AmountRequest req) {
        UUID merchantId = MerchantContext.require();
        cards.loadCard(merchantId, cardId, req.amountMinor());
        return CardView.of(cards.getCard(merchantId, cardId));
    }

    @PostMapping("/{cardId}/spend")
    public CardView spend(@PathVariable UUID cardId, @Valid @RequestBody SpendRequest req) {
        UUID merchantId = MerchantContext.require();
        cards.spend(merchantId, cardId, req.amountMinor(), req.description());
        return CardView.of(cards.getCard(merchantId, cardId));
    }

    @PostMapping("/{cardId}/freeze")
    public CardView freeze(@PathVariable UUID cardId) {
        UUID merchantId = MerchantContext.require();
        cards.freeze(merchantId, cardId);
        return CardView.of(cards.getCard(merchantId, cardId));
    }

    @PostMapping("/{cardId}/unfreeze")
    public CardView unfreeze(@PathVariable UUID cardId) {
        UUID merchantId = MerchantContext.require();
        cards.unfreeze(merchantId, cardId);
        return CardView.of(cards.getCard(merchantId, cardId));
    }

    @PostMapping("/{cardId}/close")
    public CardView close(@PathVariable UUID cardId) {
        UUID merchantId = MerchantContext.require();
        cards.close(merchantId, cardId);
        return CardView.of(cards.getCard(merchantId, cardId));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record IssueRequest(
            @NotBlank String holderRef,
            @NotNull CardType type,
            @NotBlank String currency) {}

    public record AmountRequest(@NotNull @Positive Long amountMinor) {}

    public record SpendRequest(@NotNull @Positive Long amountMinor, String description) {}

    public record TransactionView(String type, long amountMinor, String ledgerEntryId, String description, Instant createdAt) {
        static TransactionView of(CardTransaction t) {
            return new TransactionView(
                    t.getType().name(), t.getAmountMinor(), t.getLedgerEntryId().toString(),
                    t.getDescription(), t.getCreatedAt());
        }
    }

    public record CardSummary(
            String id, String holderRef, String type, String maskedPan, String currency,
            long balanceMinor, String status, Instant createdAt, Instant closedAt) {
        static CardSummary of(VirtualCard c) {
            return new CardSummary(
                    c.getId().toString(), c.getHolderRef(), c.getType().name(), c.getMaskedPan(), c.getCurrency(),
                    c.getBalanceMinor(), c.getStatus().name(), c.getCreatedAt(), c.getClosedAt());
        }
    }

    public record CardView(CardSummary card, List<TransactionView> transactions) {
        static CardView of(CardService.CardWithTransactions c) {
            return new CardView(
                    CardSummary.of(c.card()),
                    c.transactions().stream().map(TransactionView::of).toList());
        }
    }
}
