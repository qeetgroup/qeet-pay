/**
 * Accounting integrations (PRD Module 11.3) — exports a period's bookkeeping to external accounting
 * systems. Reads the {@code ledger} module's journal entries and the {@code gst} module's invoices
 * for a window, renders them for a target (Tally Prime import XML / Zoho Books / a generic webhook),
 * records each run as an {@code AccountingSync} (target, period, status, record count, external ref,
 * and the generated document for re-download), and emits an outbox event. Live Zoho is gated behind
 * {@code @ConditionalOnProperty} creds; a {@link com.qeetgroup.qeetpay.accounting.SandboxAccountingConnector}
 * no-op stands in when they are absent (same shape as {@code kyb}'s sandbox adapter). Read-only
 * against {@code ledger.*}/{@code gst.*} — this module never posts to the ledger. Merchant-scoped
 * via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Accounting",
        allowedDependencies = {"platform", "ledger", "gst"})
package com.qeetgroup.qeetpay.accounting;
