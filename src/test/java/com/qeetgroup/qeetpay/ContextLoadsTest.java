package com.qeetgroup.qeetpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the full context against a real Postgres (Testcontainers): Flyway applies V1+V2, JPA
 * validates every entity against that schema, and the {@code test} security profile starts without
 * a live Qeet ID (no JWT decoder, no network).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ContextLoadsTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void contextLoads() {
        // Context startup (Flyway + JPA schema validation) is the assertion.
    }
}
