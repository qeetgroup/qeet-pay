/**
 * ESG / carbon-footprint module (PRD Module 16, TAD §5, Phase 2/3). Estimates the carbon footprint of
 * each payment from its acceptance method and amount, and lets a merchant purchase carbon offsets.
 * Footprint estimates are informational and never touch the ledger; buying offsets costs money and
 * posts a balanced entry (debit {@code fees} / credit {@code settlement}). Both carbon records and
 * offsets are append-only. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "ESG")
package com.qeetgroup.qeetpay.esg;
