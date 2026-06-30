/**
 * Merchants module — the tenant aggregate. A merchant is the unit of multi-tenancy in Qeet Pay
 * (TAD §6.1). Onboarding seeds a default chart of accounts in the {@code ledger} module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Merchants")
package com.qeetgroup.qeetpay.merchants;
