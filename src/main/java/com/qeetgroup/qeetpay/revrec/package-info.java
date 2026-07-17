/**
 * Revenue Recognition module (IndAS 115 / ASC 606; PRD Module 11, TAD §5 "RevRec", Phase 2) — the
 * accrual overlay on the cash-basis billing/gst postings. Cash collected upfront for a service
 * delivered over time is deferred (debit {@code settlement} / credit {@code deferred_revenue}) and
 * earned ratably: each period, {@link com.qeetgroup.qeetpay.revrec.RevRecService} posts a
 * recognition entry (debit {@code deferred_revenue} / credit {@code revenue}) via the {@code ledger}
 * module. Σ(recognitions) = the contract total, so deferred revenue nets to zero at completion.
 * Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "RevRec")
package com.qeetgroup.qeetpay.revrec;
