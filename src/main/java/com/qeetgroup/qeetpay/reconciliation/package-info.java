/**
 * Reconciliation & Settlements module (TAD §6.2, §7.1) — the financial-integrity layer over the
 * append-only ledger. Ingesting a provider settlement report records the money movement (debit
 * bank + provider fees / credit the settlement holding account) and then reconciles every reported
 * line against the captured payments, flagging discrepancies for human review. A nodal check
 * asserts the holding account never goes negative (never settle out more than was captured).
 * Reads payments through the {@code payments} module's public API; posts through {@code ledger}.
 * Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reconciliation")
package com.qeetgroup.qeetpay.reconciliation;
