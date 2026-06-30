package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.fraud.FraudClient;
import com.qeetgroup.qeetpay.fraud.FraudDecision;
import com.qeetgroup.qeetpay.fraud.FraudDecisionType;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The fraud gate (TAD §8.3): when the fraud client returns BLOCK, the payment is failed at create
 * and never authorized. A {@code @Primary} test client forces the BLOCK verdict (no real fraud-svc).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(FraudBlockTest.BlockingFraudConfig.class)
class FraudBlockTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;

    @Test
    void blockedPaymentIsFailedNotAuthorized() {
        UUID merchantId =
                merchants.create("fr-" + UUID.randomUUID().toString().substring(0, 8), "Fraud Co")
                        .merchant()
                        .getId();

        Payment p = payments.create(merchantId, 499900, "INR", PaymentMethod.UPI, "risky", false);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @TestConfiguration
    static class BlockingFraudConfig {
        @Bean
        @Primary
        FraudClient blockingFraudClient() {
            return check -> new FraudDecision(95, FraudDecisionType.BLOCK, List.of("test_block"));
        }
    }
}
