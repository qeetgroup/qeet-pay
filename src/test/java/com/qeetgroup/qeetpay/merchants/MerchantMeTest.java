package com.qeetgroup.qeetpay.merchants;

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

/** {@code /v1/merchants/me}: a merchant reads back its own tenant record via {@link MerchantService#current}. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MerchantMeTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;

    @Test
    void currentReturnsTheMerchantsOwnAggregate() {
        String slug = "me-" + UUID.randomUUID().toString().substring(0, 8);
        UUID merchantId = merchants.create(slug, "Me Co").merchant().getId();

        Merchant me = merchants.current(merchantId);
        assertThat(me.getId()).isEqualTo(merchantId);
        assertThat(me.getSlug()).isEqualTo(slug);
        assertThat(me.getName()).isEqualTo("Me Co");
        assertThat(me.getStatus()).isEqualTo("active");
    }
}
