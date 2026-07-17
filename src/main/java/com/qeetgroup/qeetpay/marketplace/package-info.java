/**
 * Marketplace module (PRD Module 13, TAD §5 "Marketplace", Phase 2) — split settlements for a
 * platform merchant acting as an e-commerce operator. A collected buyer payment is split across
 * registered sellers: the operator keeps a commission (+ GST on it) and deducts statutory TCS
 * (CGST Act §52) and TDS (Income-Tax §194-O), leaving the seller's net payable. Each split posts a
 * balanced entry via the {@code ledger} module (debit settlement / credit revenue + tax_payable +
 * liability); cancelling a split posts the exact offsetting entry. Per-seller GST/TCS/TDS is
 * retained for GSTR-8 / settlement audit. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Marketplace")
package com.qeetgroup.qeetpay.marketplace;
