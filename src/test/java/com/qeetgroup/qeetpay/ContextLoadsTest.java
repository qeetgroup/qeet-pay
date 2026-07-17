package com.qeetgroup.qeetpay;

import org.junit.jupiter.api.Test;

/**
 * Boots the full context against a real Postgres (Testcontainers): Flyway applies the migrations, JPA
 * validates every entity against that schema, and the {@code test} security profile starts without a
 * live Qeet ID (no JWT decoder, no network).
 *
 * <p>Adopts {@link AbstractIntegrationTest} — it shares the JVM-wide singleton Postgres and reuses the
 * cached context, so the whole suite can run without exhausting Docker (see that base class).
 */
class ContextLoadsTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Context startup (Flyway + JPA schema validation) is the assertion.
    }
}
