/**
 * Embedded lending module (PRD Module 10, TAD §5 "Embedded Finance", Phase 2) — AA-powered
 * working-capital advances repaid as a % of daily settlement (revenue-based financing). An offer is
 * underwritten from settlement/GST history through the pluggable {@link
 * com.qeetgroup.qeetpay.lending.UnderwritingAdapter}; on acceptance the advance is disbursed and
 * repayments are swept from settlements via the {@code ledger} module (debit settlement + fees /
 * credit a dedicated {@code loan_payable} account on disbursement; the reverse per repayment) until
 * the total repayable clears. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Lending")
package com.qeetgroup.qeetpay.lending;
