/**
 * Virtual cards module (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). Issues prepaid virtual
 * cards — employee expense cards and customer wallet cards — funded from a merchant's balance.
 * Loading a card moves cash into an on-demand {@code card_liability} account (debit {@code settlement}
 * / credit {@code card_liability}), which the platform now owes the holder; spending reverses it
 * (debit {@code card_liability} / credit {@code settlement}). Every load/spend is a balanced ledger
 * posting and an append-only transaction. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cards")
package com.qeetgroup.qeetpay.cards;
