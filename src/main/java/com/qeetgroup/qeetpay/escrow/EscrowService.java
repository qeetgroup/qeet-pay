package com.qeetgroup.qeetpay.escrow;

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
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Digital escrow (PRD Module 10, TAD §5). Holding funds moves cash into an on-demand
 * {@code escrow_payable} liability; releasing moves it to the seller's {@code liability}; refunding
 * returns it to {@code settlement}. Partial releases/refunds are allowed until the escrow is fully
 * allocated. Every movement is a balanced ledger posting, an append-only event, and an outbox event.
 */
@Service
public class EscrowService {

    private static final String ESCROW_PAYABLE = "escrow_payable";

    private final EscrowAgreementRepository agreements;
    private final EscrowEventRepository events;
    private final LedgerService ledger;
    private final MerchantScope merchantScope;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public EscrowService(
            EscrowAgreementRepository agreements,
            EscrowEventRepository events,
            LedgerService ledger,
            MerchantScope merchantScope,
            OutboxService outbox,
            ObjectMapper objectMapper) {
        this.agreements = agreements;
        this.events = events;
        this.ledger = ledger;
        this.merchantScope = merchantScope;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** Opens an escrow and holds the buyer's funds (debit settlement / credit escrow_payable). */
    @Transactional
    public AgreementWithEvents hold(
            UUID merchantId, String buyerRef, String sellerRef, long amountMinor,
            String currency, String description) {
        merchantScope.apply(merchantId);
        if (buyerRef == null || buyerRef.isBlank() || sellerRef == null || sellerRef.isBlank()) {
            throw new IllegalArgumentException("buyerRef and sellerRef are required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID escrowPayable = escrowPayable(merchantId, currency);
        UUID entryId =
                ledger.postEntry(
                        merchantId, "escrow hold " + buyerRef + "->" + sellerRef, currency,
                        List.of(
                                new LedgerLineInput(settlement, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(escrowPayable, Direction.CREDIT, amountMinor)));

        EscrowAgreement agreement =
                agreements.save(
                        new EscrowAgreement(merchantId, buyerRef, sellerRef, currency, amountMinor, description, entryId));
        EscrowEvent event =
                events.save(new EscrowEvent(agreement.getId(), merchantId, EscrowEventType.HOLD, amountMinor, entryId, description));
        outbox.enqueue(merchantId, "escrow.held", eventJson(agreement, event));
        return new AgreementWithEvents(agreement, List.of(event));
    }

    /** Releases (part of) the held funds to the seller (debit escrow_payable / credit liability). */
    @Transactional
    public EscrowAgreement release(UUID merchantId, UUID agreementId, long amountMinor, String note) {
        merchantScope.apply(merchantId);
        EscrowAgreement agreement = load(merchantId, agreementId);
        UUID escrowPayable = escrowPayable(merchantId, agreement.getCurrency());
        UUID liability = ledger.accountByCode(merchantId, "liability").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "escrow release " + agreementId, agreement.getCurrency(),
                        List.of(
                                new LedgerLineInput(escrowPayable, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(liability, Direction.CREDIT, amountMinor)));
        agreement.applyRelease(amountMinor);
        agreements.save(agreement);
        EscrowEvent event =
                events.save(new EscrowEvent(agreementId, merchantId, EscrowEventType.RELEASE, amountMinor, entryId, note));
        outbox.enqueue(merchantId, "escrow.released", eventJson(agreement, event));
        return agreement;
    }

    /** Refunds (part of) the held funds to the buyer (debit escrow_payable / credit settlement). */
    @Transactional
    public EscrowAgreement refund(UUID merchantId, UUID agreementId, long amountMinor, String note) {
        merchantScope.apply(merchantId);
        EscrowAgreement agreement = load(merchantId, agreementId);
        UUID escrowPayable = escrowPayable(merchantId, agreement.getCurrency());
        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID entryId =
                ledger.postEntry(
                        merchantId, "escrow refund " + agreementId, agreement.getCurrency(),
                        List.of(
                                new LedgerLineInput(escrowPayable, Direction.DEBIT, amountMinor),
                                new LedgerLineInput(settlement, Direction.CREDIT, amountMinor)));
        agreement.applyRefund(amountMinor);
        agreements.save(agreement);
        EscrowEvent event =
                events.save(new EscrowEvent(agreementId, merchantId, EscrowEventType.REFUND, amountMinor, entryId, note));
        outbox.enqueue(merchantId, "escrow.refunded", eventJson(agreement, event));
        return agreement;
    }

    @Transactional(readOnly = true)
    public AgreementWithEvents getAgreement(UUID merchantId, UUID agreementId) {
        merchantScope.apply(merchantId);
        EscrowAgreement agreement = load(merchantId, agreementId);
        return new AgreementWithEvents(agreement, events.findByAgreementIdOrderByCreatedAt(agreementId));
    }

    @Transactional(readOnly = true)
    public List<EscrowAgreement> listAgreements(UUID merchantId) {
        merchantScope.apply(merchantId);
        return agreements.findByMerchantIdOrderByCreatedAtDesc(merchantId);
    }

    private UUID escrowPayable(UUID merchantId, String currency) {
        return ledger.ensureAccount(merchantId, ESCROW_PAYABLE, "Escrow payable", AccountType.LIABILITY, currency)
                .getId();
    }

    private EscrowAgreement load(UUID merchantId, UUID agreementId) {
        return agreements
                .findById(agreementId)
                .filter(a -> a.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new EscrowNotFoundException("no escrow " + agreementId));
    }

    /** An escrow agreement plus its event history. */
    public record AgreementWithEvents(EscrowAgreement agreement, List<EscrowEvent> events) {}

    private String eventJson(EscrowAgreement a, EscrowEvent e) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("escrowId", a.getId().toString());
        b.put("type", e.getType().name());
        b.put("amountMinor", e.getAmountMinor());
        b.put("status", a.getStatus().name());
        b.put("remainingMinor", a.remainingMinor());
        b.put("ledgerEntryId", e.getLedgerEntryId().toString());
        try {
            return objectMapper.writeValueAsString(b);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialise escrow event", ex);
        }
    }
}
