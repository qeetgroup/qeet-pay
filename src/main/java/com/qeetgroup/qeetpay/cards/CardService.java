package com.qeetgroup.qeetpay.cards;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ledger.AccountType;
import com.qeetgroup.qeetpay.ledger.Direction;
import com.qeetgroup.qeetpay.ledger.LedgerLineInput;
import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxService;
import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Virtual cards (PRD Module 10, TAD §5). Issues prepaid expense/wallet cards and moves cash on and
 * off them through the ledger: loading a card debits {@code settlement} / credits an on-demand
 * {@code card_liability} account (the platform now owes the holder), and spending reverses it (debit
 * {@code card_liability} / credit {@code settlement}). Every load/spend is a balanced ledger posting,
 * an append-only transaction, and an outbox event. All writes are merchant-scoped via RLS.
 */
@Service
public class CardService {

    private static final String CARD_LIABILITY = "card_liability";

    private final VirtualCardRepository cards;
    private final CardTransactionRepository txns;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public CardService(
            VirtualCardRepository cards,
            CardTransactionRepository txns,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.cards = cards;
        this.txns = txns;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Issues a new ACTIVE card with a zero balance and a freshly generated masked PAN. */
    @Transactional
    public VirtualCard issueCard(UUID merchantId, String holderRef, CardType type, String currency) {
        merchantScope.apply(merchantId);
        if (holderRef == null || holderRef.isBlank()) {
            throw new IllegalArgumentException("holderRef is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
        VirtualCard card =
                cards.save(new VirtualCard(merchantId, holderRef, type, generateMaskedPan(), currency));
        outbox.enqueue(merchantId, "card.issued", cardJson(card));
        return card;
    }

    /** Loads funds onto an ACTIVE card (debit settlement / credit card_liability). */
    @Transactional
    public VirtualCard loadCard(UUID merchantId, UUID cardId, long amountMinor) {
        merchantScope.apply(merchantId);
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        VirtualCard card = load(merchantId, cardId);
        if (!card.isActive()) {
            throw new IllegalStateException("card is " + card.getStatus());
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID cardLiability = cardLiability(merchantId, card.getCurrency());
        UUID entryId =
                ledger.postEntry(
                        merchantId, "card load " + cardId, card.getCurrency(),
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(cardLiability, Direction.CREDIT, amountMinor)));
        card.load(amountMinor);
        cards.save(card);
        CardTransaction txn =
                txns.save(new CardTransaction(cardId, merchantId, CardTxnType.LOAD, amountMinor, entryId, null));
        outbox.enqueue(merchantId, "card.loaded", txnJson(card, txn));
        return card;
    }

    /** Spends from an ACTIVE card (debit card_liability / credit settlement). */
    @Transactional
    public VirtualCard spend(UUID merchantId, UUID cardId, long amountMinor, String description) {
        merchantScope.apply(merchantId);
        VirtualCard card = load(merchantId, cardId);

        UUID cardLiability = cardLiability(merchantId, card.getCurrency());
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "card spend " + cardId, card.getCurrency(),
                        List.of(
                                new LedgerLineInput(cardLiability, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(settlement, Direction.CREDIT, amountMinor)));
        card.spend(amountMinor);
        cards.save(card);
        CardTransaction txn =
                txns.save(new CardTransaction(cardId, merchantId, CardTxnType.SPEND, amountMinor, entryId, description));
        outbox.enqueue(merchantId, "card.spent", txnJson(card, txn));
        return card;
    }

    @Transactional
    public VirtualCard freeze(UUID merchantId, UUID cardId) {
        merchantScope.apply(merchantId);
        VirtualCard card = load(merchantId, cardId);
        card.freeze();
        cards.save(card);
        outbox.enqueue(merchantId, "card.frozen", cardJson(card));
        return card;
    }

    @Transactional
    public VirtualCard unfreeze(UUID merchantId, UUID cardId) {
        merchantScope.apply(merchantId);
        VirtualCard card = load(merchantId, cardId);
        card.unfreeze();
        cards.save(card);
        outbox.enqueue(merchantId, "card.unfrozen", cardJson(card));
        return card;
    }

    @Transactional
    public VirtualCard close(UUID merchantId, UUID cardId) {
        merchantScope.apply(merchantId);
        VirtualCard card = load(merchantId, cardId);
        card.close();
        cards.save(card);
        outbox.enqueue(merchantId, "card.closed", cardJson(card));
        return card;
    }

    @Transactional(readOnly = true)
    public CardWithTransactions getCard(UUID merchantId, UUID cardId) {
        merchantScope.apply(merchantId);
        VirtualCard card = load(merchantId, cardId);
        return new CardWithTransactions(card, txns.findByCardIdOrderByCreatedAt(cardId));
    }

    @Transactional(readOnly = true)
    public List<VirtualCard> listCards(UUID merchantId) {
        merchantScope.apply(merchantId);
        return cards.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private UUID cardLiability(UUID merchantId, String currency) {
        return ledger.ensureAccount(merchantId, CARD_LIABILITY, "Card liability", AccountType.LIABILITY, currency)
                .getId();
    }

    private VirtualCard load(UUID merchantId, UUID cardId) {
        return cards
                .findById(cardId)
                .filter(c -> c.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new CardNotFoundException("no card " + cardId));
    }

    /** A masked PAN of the form {@code "XXXX XXXX XXXX 1234"} (sandbox — no real card is minted). */
    private String generateMaskedPan() {
        return "XXXX XXXX XXXX " + String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
    }

    /** A card plus its transaction history. */
    public record CardWithTransactions(VirtualCard card, List<CardTransaction> transactions) {}

    private String cardJson(VirtualCard c) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("cardId", c.getId().toString());
        b.put("holderRef", c.getHolderRef());
        b.put("type", c.getType().name());
        b.put("maskedPan", c.getMaskedPan());
        b.put("balanceMinor", c.getBalanceMinor());
        b.put("status", c.getStatus().name());
        return write(b);
    }

    private String txnJson(VirtualCard c, CardTransaction t) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("cardId", c.getId().toString());
        b.put("txnId", t.getId().toString());
        b.put("type", t.getType().name());
        b.put("amountMinor", t.getAmountMinor());
        b.put("balanceMinor", c.getBalanceMinor());
        b.put("ledgerEntryId", t.getLedgerEntryId().toString());
        return write(b);
    }

    private String write(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise card event", e);
        }
    }
}
