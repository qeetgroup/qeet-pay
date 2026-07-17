package com.qeetgroup.qeetpay.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.merchants.MerchantService;
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

/**
 * Smart-orchestration feedback loop (PRD Module 07.3): each real payment updates the chosen provider's
 * scorecard (auth rate + health), a repeated-failure run trips the provider DOWN so routing avoids it,
 * and the cost basis is configurable per provider.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProviderRoutingTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired PaymentService payments;
    @Autowired ProviderRoutingService routing;

    @Test
    void paymentsFeedTheSandboxScorecard() {
        UUID merchantId = newMerchant();

        Payment authorized = payments.create(merchantId, 100_000, "INR", PaymentMethod.UPI, "ok", false);
        payments.capture(merchantId, authorized.getId());

        ProviderScorecard sandbox = scorecard(merchantId, "SANDBOX");
        assertThat(sandbox.getAttempts()).isEqualTo(2); // AUTHORIZE + CAPTURE
        assertThat(sandbox.getSuccesses()).isEqualTo(2);
        assertThat(sandbox.authRate()).isEqualTo(1.0);
        assertThat(sandbox.getHealth()).isEqualTo(ProviderHealth.HEALTHY);
    }

    @Test
    void aFailureRunTripsProviderDownAndRoutingAvoidsIt() {
        UUID merchantId = newMerchant();
        // Five simulated authorize failures in a row trip the sandbox scorecard to DOWN.
        for (int i = 0; i < ProviderScorecard.DOWN_THRESHOLD; i++) {
            payments.create(merchantId, 50_000, "INR", PaymentMethod.UPI, "bad-" + i, true);
        }

        ProviderScorecard sandbox = scorecard(merchantId, "SANDBOX");
        assertThat(sandbox.getFailures()).isEqualTo(ProviderScorecard.DOWN_THRESHOLD);
        assertThat(sandbox.getHealth()).isEqualTo(ProviderHealth.DOWN);

        // With SANDBOX DOWN and a healthy alternative available, routing avoids SANDBOX.
        String chosen = routing.chooseProviderName(merchantId, java.util.List.of("SANDBOX", "RAZORPAY"));
        assertThat(chosen).isEqualTo("RAZORPAY");
    }

    @Test
    void costBasisIsConfigurable() {
        UUID merchantId = newMerchant();
        routing.setCost(merchantId, "SANDBOX", 15); // 0.15% (UPI)
        assertThat(scorecard(merchantId, "SANDBOX").getCostBps()).isEqualTo(15);
    }

    private UUID newMerchant() {
        return merchants.create("orc-" + UUID.randomUUID().toString().substring(0, 8), "Orchestration Co")
                .merchant().getId();
    }

    private ProviderScorecard scorecard(UUID merchantId, String provider) {
        return routing.scorecards(merchantId).stream()
                .filter(s -> s.getProvider().equals(provider))
                .findFirst()
                .orElseThrow();
    }
}
