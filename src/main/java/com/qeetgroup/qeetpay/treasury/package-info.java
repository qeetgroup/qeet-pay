/**
 * Treasury automation module (PRD Novel N3 "Programmable money & treasury automation", TAD §5). A
 * merchant defines <b>sweep rules</b> that move idle cash between its own ledger accounts — e.g. move
 * everything above a threshold out of {@code settlement} into {@code bank}, retaining a buffer. When a
 * rule fires, the sweep is a balanced ledger posting (debit target / credit source for asset-like
 * accounts, or the reverse), recorded as an append-only {@link com.qeetgroup.qeetpay.treasury.SweepExecution}
 * and emitted to the outbox as {@code treasury.sweep.executed}. An advisory idle-cash recommendation
 * reuses the analytics cash-flow forecast (read-only). Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Treasury",
        // platform: outbox + tenancy/RLS. ledger: balances + balanced sweep postings.
        // analytics: read-only cash-flow forecast behind the idle-cash recommendation. No cycle —
        // nothing in platform/ledger/analytics depends on treasury.
        allowedDependencies = {"platform", "ledger", "analytics"})
package com.qeetgroup.qeetpay.treasury;
