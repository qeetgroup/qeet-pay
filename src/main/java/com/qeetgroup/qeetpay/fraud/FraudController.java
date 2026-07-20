package com.qeetgroup.qeetpay.fraud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Fraud decision audit API (PRD Module 08.1/8.4). Read-only: browse the merchant-scoped fraud-scoring
 * trail, and inspect a single decision with its full explainability payload (top contributing features).
 */
@Tag(
        name = "Fraud",
        description = "Fraud-scoring decision audit — score, verdict, and the Explainable-AI top reasons.")
@RestController
@RequestMapping("/v1/fraud")
public class FraudController {

    private final FraudDecisionService decisions;
    private final ObjectMapper objectMapper;

    public FraudController(FraudDecisionService decisions, ObjectMapper objectMapper) {
        this.decisions = decisions;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/decisions")
    public List<DecisionSummary> list() {
        return decisions.list(MerchantContext.require()).stream().map(DecisionSummary::of).toList();
    }

    @GetMapping("/decisions/{id}")
    public DecisionView get(@PathVariable UUID id) {
        return toView(decisions.get(MerchantContext.require(), id));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    /** List row — the verdict + score without the (potentially large) explanation payload. */
    public record DecisionSummary(
            String id,
            String paymentId,
            int score,
            String decision,
            String model,
            Instant createdAt) {
        static DecisionSummary of(FraudDecisionRecord r) {
            return new DecisionSummary(
                    r.getId().toString(),
                    r.getPaymentId() == null ? null : r.getPaymentId().toString(),
                    r.getScore(),
                    r.getDecision(),
                    r.getModel(),
                    r.getCreatedAt());
        }
    }

    /** Full view — adds the parsed Explainable-AI top contributing features. */
    public record DecisionView(
            String id,
            String paymentId,
            int score,
            String decision,
            String model,
            List<FraudReason> topReasons,
            String aiDecisionId,
            Instant createdAt) {}

    private DecisionView toView(FraudDecisionRecord r) {
        List<FraudReason> topReasons;
        try {
            topReasons = objectMapper.readValue(r.getTopReasons(), new TypeReference<List<FraudReason>>() {});
        } catch (Exception e) {
            topReasons = List.of();
        }
        return new DecisionView(
                r.getId().toString(),
                r.getPaymentId() == null ? null : r.getPaymentId().toString(),
                r.getScore(),
                r.getDecision(),
                r.getModel(),
                topReasons,
                r.getAiDecisionId() == null ? null : r.getAiDecisionId().toString(),
                r.getCreatedAt());
    }
}
