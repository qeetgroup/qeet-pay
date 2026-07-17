/**
 * Payment links module (PRD Module 01 "links, pages, checkout surface", Phase 2). A merchant creates a
 * shareable link for a fixed or customer-entered amount; paying it drives a real payment through the
 * {@code payments} module (create → authorize → capture), which posts the money-in ledger entry, and
 * the link records the resulting payment id. Lifecycle: ACTIVE → PAID (or EXPIRED / CANCELLED).
 * Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "PaymentLinks")
package com.qeetgroup.qeetpay.paymentlinks;
