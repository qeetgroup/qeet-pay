@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        // platform + ledger: existing analytics/cash-flow reads.
        // reconciliation + filing + kyb: read-only public services composed by the Module 12.6
        // Unified Financial-Health & Compliance Dashboard (ComplianceHealthService). Fraud posture is
        // read from platform AppProperties (the fraud module is stateless), so fraud is not a dependency.
        // No cycle: nothing depends on the analytics module.
        allowedDependencies = {"platform", "ledger", "reconciliation", "filing", "kyb"})
package com.qeetgroup.qeetpay.analytics;
