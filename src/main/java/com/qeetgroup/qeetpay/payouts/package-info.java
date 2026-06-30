/**
 * Payouts module — disbursements (TAD Module 02): create → approve (maker-checker) → process, with
 * the payout driving a balanced double-entry posting (debit liability / credit bank) in the
 * {@code ledger} module. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payouts")
package com.qeetgroup.qeetpay.payouts;
