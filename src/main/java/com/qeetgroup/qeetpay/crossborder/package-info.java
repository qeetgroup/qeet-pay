/**
 * Cross-border collection module (PRD Module 14, TAD §5 "Cross-Border", Phase 2/3). An Indian
 * exporter raises a foreign-currency export invoice (zero-rated under LUT, with a FEMA/RBI purpose
 * code); a foreign inward remittance is converted to INR at the captured FX rate (via the pluggable
 * {@link com.qeetgroup.qeetpay.crossborder.FxRateAdapter}), the FIRA reference is recorded, and the
 * INR equivalent posts money-in to the {@code ledger} module. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "CrossBorder")
package com.qeetgroup.qeetpay.crossborder;
