package com.qeetgroup.qeetpay.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import com.qeetgroup.qeetpay.platform.outbox.OutboxEvent;
import com.qeetgroup.qeetpay.platform.outbox.OutboxRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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
 * AI gateway safety-matrix flow (PRD §6.4), exercised through the offline {@link SandboxAiModelClient}.
 * Covers: PII/PAN masking before persistence; human-review gating on a money-affecting decision type;
 * deterministic fallback when the model client throws; and that every call writes an append-only
 * {@code ai_decision} row plus an {@code ai.decision.recorded} outbox event.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AiGatewayFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final Supplier<String> FALLBACK = () -> "{\"decision\":\"deterministic-rule\"}";

    @Autowired MerchantService merchants;
    @Autowired AiGateway gateway;
    @Autowired AiDecisionRepository decisions;
    @Autowired OutboxRepository outbox;

    @Test
    void masksPiiAndPanBeforePersisting() {
        // The masker itself removes every recognised PII pattern.
        PiiMasker masker = new PiiMasker();
        assertThat(masker.mask("PAN ABCDE1234F")).doesNotContain("ABCDE1234F").contains("[PAN]");
        assertThat(masker.mask("Aadhaar 1234 5678 9012")).doesNotContain("1234 5678 9012").contains("[AADHAAR]");
        assertThat(masker.mask("card 4111 1111 1111 1111")).doesNotContain("4111").contains("[CARD]");
        assertThat(masker.mask("mail john@example.com")).doesNotContain("john@example.com").contains("[EMAIL]");
        assertThat(masker.mask("call 9876543210")).doesNotContain("9876543210").contains("[PHONE]");

        // And the gateway persists only the masked form + a hash — never the raw PAN.
        UUID merchantId = newMerchant();
        gateway.evaluate(
                new AiRequest(merchantId, "gst.classification", null, "classify supply for PAN ABCDE1234F",
                        false, false, Set.of("pay:read"), 0.5),
                FALLBACK);
        AiDecision stored = decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId).get(0);
        assertThat(stored.getMaskedInput()).doesNotContain("ABCDE1234F").contains("[PAN]");
        assertThat(stored.getInputHash()).isNotBlank();
    }

    @Test
    void moneyAffectingDecisionRequiresHumanReview() {
        UUID merchantId = newMerchant();

        // A money-affecting type (lending) with no human review fails closed to the deterministic path.
        AiDecisionResult unreviewed =
                gateway.evaluate(
                        new AiRequest(merchantId, "lending.decision", null, "advance working capital",
                                true, false, Set.of("lending:decide"), 0.5),
                        FALLBACK);
        assertThat(unreviewed.requiresHumanReview()).isTrue();
        assertThat(unreviewed.fellBack()).isTrue();
        assertThat(unreviewed.outputJson()).isEqualTo("{\"decision\":\"deterministic-rule\"}");

        // Once a human has reviewed, the (confident) model result is used.
        AiDecisionResult reviewed =
                gateway.evaluate(
                        new AiRequest(merchantId, "lending.decision", null, "advance working capital",
                                true, true, Set.of("lending:decide"), 0.5),
                        FALLBACK);
        assertThat(reviewed.requiresHumanReview()).isFalse();
        assertThat(reviewed.fellBack()).isFalse();
        assertThat(reviewed.outputJson()).contains("sandbox-offline-stub");
    }

    @Test
    void modelErrorFallsBackDeterministically() {
        UUID merchantId = newMerchant();
        // "fail_" makes the sandbox client throw (simulated timeout/error).
        AiDecisionResult result =
                gateway.evaluate(
                        new AiRequest(merchantId, "gst.classification", null, "please fail_now",
                                false, false, Set.of(), 0.5),
                        FALLBACK);
        assertThat(result.fellBack()).isTrue();
        assertThat(result.outputJson()).isEqualTo("{\"decision\":\"deterministic-rule\"}");
    }

    @Test
    void writesDecisionRowAndOutboxEvent() {
        UUID merchantId = newMerchant();
        AiDecisionResult result =
                gateway.evaluate(
                        new AiRequest(merchantId, "gst.classification", null, "classify this item",
                                false, false, Set.of("pay:read"), 0.5),
                        FALLBACK);

        List<AiDecision> rows = decisions.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        assertThat(rows).extracting(AiDecision::getId).contains(result.decisionId());

        List<OutboxEvent> events = outbox.findAll();
        assertThat(events).anyMatch(e -> e.getSubject().endsWith("ai.decision.recorded"));
    }

    private UUID newMerchant() {
        return merchants.create("ai-" + UUID.randomUUID().toString().substring(0, 8), "AI Co")
                .merchant().getId();
    }
}
