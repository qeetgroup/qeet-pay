package com.qeetgroup.qeetpay.cards;

import java.util.UUID;

/**
 * The card-issuing rail boundary (PRD Module 10.3): where {@link CardService} hands card lifecycle off
 * to an issuing network. The default {@link SandboxCardIssuingProvider} is an internal simulation (no
 * real card is minted); {@link LiveCardIssuingProvider} talks to an M2P / Decentro-style issuing API
 * when {@code qeetpay.cards.enabled=true}.
 *
 * <p><b>Reference correlation.</b> Qeet Pay does not (yet) persist the network card reference on
 * {@link VirtualCard} — there is no column for it and this change adds no migration. The internal
 * {@link VirtualCard#getId() card id} is therefore sent as the <em>client reference</em> at issue time
 * ({@link CardIssueRequest#cardId()}) and reused as the reference for every subsequent lifecycle /
 * authorization call, so a live rail can correlate a card by the id Qeet Pay already stores. The
 * network reference returned from {@link #issue} is surfaced to the caller but not persisted.
 */
public interface CardIssuingProvider {

    /** Issues a card on the rail and returns the network reference plus its {@code last4} / expiry. */
    IssuedCard issue(CardIssueRequest request);

    /** Temporarily blocks the card at the rail. */
    void freeze(String networkCardRef);

    /** Re-activates a previously frozen card at the rail. */
    void unfreeze(String networkCardRef);

    /** Permanently retires the card at the rail. */
    void close(String networkCardRef);

    /** Optional: authorizes a funding/load of the card at the rail. Default is a no-op (sandbox). */
    default void authorizeLoad(CardAuthorization authorization) {}

    /** Optional: authorizes a spend against the card at the rail; throws if declined. Default no-op. */
    default void authorizeSpend(CardAuthorization authorization) {}

    /**
     * What Qeet Pay asks the rail to issue. {@code cardId} is the internal {@link VirtualCard} id, sent
     * as the client reference so lifecycle / authorization calls can correlate later.
     */
    record CardIssueRequest(UUID cardId, UUID merchantId, String holderRef, CardType type, String currency) {}

    /** What the rail returns from {@link #issue}: the network card reference plus display fields. */
    record IssuedCard(String networkCardRef, String last4, String expiry) {}

    /** A load / spend authorization against a previously issued card. */
    record CardAuthorization(String networkCardRef, long amountMinor, String currency) {}
}
