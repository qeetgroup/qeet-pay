package com.qeetgroup.qeetpay;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the modular-monolith boundaries (TAD §3.1): no cycles, and modules touch one another
 * only through their public API (not internals). The guardrail that keeps "extract later" viable.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(QeetPayApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }
}
