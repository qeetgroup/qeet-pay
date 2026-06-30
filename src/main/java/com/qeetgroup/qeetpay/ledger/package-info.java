/**
 * Ledger module — Qeet Pay's crown jewel: an append-only, double-entry accounting ledger
 * (TAD §6.2, §7.1). Every financial movement is a balanced set of debit/credit lines; the
 * books always net to zero. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Ledger")
package com.qeetgroup.qeetpay.ledger;
