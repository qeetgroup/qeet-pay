/**
 * Input Tax Credit / GSTR-2B reconciliation module (PRD Module 05/06, Phase 2). Records a merchant's
 * INWARD supplies (purchase invoices from suppliers), reconciles each against supplier-filed GSTR-2B
 * data, and reports the eligible ITC. Pure compliance tracking — no ledger postings or money movement.
 * Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "ITC")
package com.qeetgroup.qeetpay.itc;
