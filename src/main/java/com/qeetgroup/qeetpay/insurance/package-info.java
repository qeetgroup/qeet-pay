/**
 * Embedded insurance module (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). A merchant issues
 * payment-protection / fraud-cover / subscription-interruption policies; each premium is collected up
 * front into an on-demand {@code insurance_reserve} liability (debit {@code settlement} / credit the
 * reserve). Claims are filed against the cover, then paid out of the reserve (debit reserve / credit
 * {@code settlement}) or rejected (no money moves). Every action is a balanced ledger posting and an
 * outbox event. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Insurance")
package com.qeetgroup.qeetpay.insurance;
