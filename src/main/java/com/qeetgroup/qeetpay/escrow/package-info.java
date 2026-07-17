/**
 * Digital escrow module (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). A buyer's payment is
 * held (debit {@code settlement} / credit an on-demand {@code escrow_payable} account) and released
 * to the seller (credit {@code liability}) on delivery/milestone confirmation, or refunded to the
 * buyer (credit {@code settlement}). Partial releases/refunds are supported and every action is an
 * append-only event carrying its balanced ledger posting. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Escrow")
package com.qeetgroup.qeetpay.escrow;
