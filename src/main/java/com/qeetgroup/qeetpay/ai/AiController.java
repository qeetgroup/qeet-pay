package com.qeetgroup.qeetpay.ai;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI gateway audit API (PRD §6.4 "Auditable"). Read-only: browse the decision trail every AI feature
 * writes through {@link AiGateway}, and check gateway health. There are deliberately no feature
 * endpoints here — features call {@link AiGateway} in-process; those APIs land with each feature.
 */
@Tag(
        name = "AI",
        description = "AI gateway decision audit — the trail every AI feature writes through the §6.4 safety matrix.")
@RestController
@RequestMapping("/v1/ai")
public class AiController {

    private final AiGateway gateway;

    public AiController(AiGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/decisions")
    public List<DecisionSummary> list() {
        return gateway.listDecisions(MerchantContext.require()).stream().map(DecisionSummary::of).toList();
    }

    @GetMapping("/decisions/{decisionId}")
    public DecisionView get(@PathVariable UUID decisionId) {
        return DecisionView.of(gateway.getDecision(MerchantContext.require(), decisionId));
    }

    @GetMapping("/health")
    public AiGateway.GatewayHealth health() {
        return gateway.health();
    }

    // ── Records ──────────────────────────────────────────────────────────────

    /** List row — the safety-matrix outcome without the (potentially large) input/output payloads. */
    public record DecisionSummary(
            String id,
            String feature,
            String model,
            double confidence,
            boolean humanReviewed,
            boolean fellBack,
            Instant createdAt) {
        static DecisionSummary of(AiDecision d) {
            return new DecisionSummary(
                    d.getId().toString(),
                    d.getFeature(),
                    d.getModel(),
                    d.getConfidence(),
                    d.isHumanReviewed(),
                    d.isFellBack(),
                    d.getCreatedAt());
        }
    }

    /** Full audit view — adds the masked input, its hash, and the returned decision payload. */
    public record DecisionView(
            String id,
            String feature,
            String model,
            String inputHash,
            String maskedInput,
            String outputJson,
            double confidence,
            boolean humanReviewed,
            boolean fellBack,
            Instant createdAt) {
        static DecisionView of(AiDecision d) {
            return new DecisionView(
                    d.getId().toString(),
                    d.getFeature(),
                    d.getModel(),
                    d.getInputHash(),
                    d.getMaskedInput(),
                    d.getOutputJson(),
                    d.getConfidence(),
                    d.isHumanReviewed(),
                    d.isFellBack(),
                    d.getCreatedAt());
        }
    }
}
