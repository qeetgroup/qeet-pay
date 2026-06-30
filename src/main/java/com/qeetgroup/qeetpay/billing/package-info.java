/**
 * Billing module — subscription billing (TAD Module 03): plans, subscriptions, and invoices. Paying
 * an invoice posts a balanced double-entry (debit settlement / credit revenue) via the {@code ledger}
 * module (cash-basis recognition; accrual/IndAS 115 is Phase 2). Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Billing")
package com.qeetgroup.qeetpay.billing;
