/**
 * Payments module — payment acceptance (TAD Module 01 / §4.1): create → authorize (via a provider)
 * → capture, with capture driving a balanced double-entry posting in the {@code ledger} module.
 * Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Payments")
package com.qeetgroup.qeetpay.payments;
