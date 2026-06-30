package com.qeetgroup.qeetpay.platform.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** A stored idempotency outcome is replayed on lookup; an unseen key is a miss (TAD §4.3). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class IdempotencyServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired IdempotencyService service;

    @Test
    void replaysStoredOutcomeAndMissesUnknownKey() {
        UUID merchantId = UUID.randomUUID();
        assertThat(service.lookup(merchantId, "key-1")).isEmpty();

        service.save(merchantId, "key-1", 201, "{\"entryId\":\"abc\"}");

        var found = service.lookup(merchantId, "key-1");
        assertThat(found).isPresent();
        assertThat(found.get().getResponseStatus()).isEqualTo(201);
        assertThat(found.get().getResponseBody()).contains("entryId");

        // Same key, different merchant => still a miss (scoped per merchant).
        assertThat(service.lookup(UUID.randomUUID(), "key-1")).isEmpty();
    }
}
