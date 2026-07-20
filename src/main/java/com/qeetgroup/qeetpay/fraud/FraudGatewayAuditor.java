package com.qeetgroup.qeetpay.fraud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qeetgroup.qeetpay.ai.AiDecisionResult;
import com.qeetgroup.qeetpay.ai.AiFeature;
import com.qeetgroup.qeetpay.ai.AiGateway;
import com.qeetgroup.qeetpay.ai.AiRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes fraud scoring through the {@code ai/AiGateway} §6.4 safety substrate and records the result.
 *
 * <p>Fraud scoring is a <em>money-affecting</em> AI feature ({@link AiFeature#FRAUD_SCORING}). Per the
 * §6.4 matrix, a money-affecting decision that has not been human-reviewed always falls closed to the
 * caller-supplied deterministic path — here the underlying {@link FraudScorer} (fraud-svc HTTP, or the
 * allow-all fallback). The gateway still masks the input (VPA/PII), records an append-only
 * {@code ai.ai_decision} row, and emits the {@code ai.decision.recorded} outbox event; this auditor
 * then persists a {@link FraudDecisionRecord} linking back to that decision.
 *
 * <p>Runs in a {@link Propagation#REQUIRES_NEW} transaction so the audit writes are isolated from the
 * payment transaction — a persistence failure here can never roll back or block a payment (scoring is
 * advisory; {@link AiGatewayFraudClient} additionally fails open around this call).
 */
@Component
public class FraudGatewayAuditor {

    private final FraudScorer delegate;
    private final AiGateway aiGateway;
    private final FraudDecisionService decisions;
    private final ObjectMapper objectMapper;

    public FraudGatewayAuditor(
            FraudScorer delegate,
            AiGateway aiGateway,
            FraudDecisionService decisions,
            ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.aiGateway = aiGateway;
        this.decisions = decisions;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FraudDecision scoreThroughGateway(FraudCheck check) {
        // Capture the deterministic decision produced inside the gateway fallback supplier.
        FraudDecision[] holder = new FraudDecision[1];

        AiRequest request =
                new AiRequest(
                        check.merchantId(),
                        AiFeature.FRAUD_SCORING.key(), // "fraud.scoring" — money-affecting
                        null,
                        buildInput(check), // VPA/PII masked by the gateway before any model call
                        true, // moneyAffecting
                        false, // humanReviewed — fraud gate auto-applies without prior human review
                        Set.of(),
                        0.5);

        AiDecisionResult result =
                aiGateway.evaluate(
                        request,
                        () -> {
                            FraudDecision d = delegate.score(check);
                            holder[0] = d;
                            return toJson(d);
                        });

        FraudDecision decision =
                holder[0] != null ? holder[0] : FraudDecision.allow("fraud scoring not evaluated");

        String model = decision.model() == null ? "none" : decision.model();
        decisions.record(
                check.merchantId(),
                check.paymentId(),
                decision.score(),
                decision.decision().name(),
                toJson(decision.topReasons()),
                model,
                result.decisionId());

        return decision;
    }

    private String buildInput(FraudCheck check) {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("paymentId", check.paymentId() == null ? null : check.paymentId().toString());
        in.put("amountMinor", check.amountMinor());
        in.put("currency", check.currency());
        in.put("method", check.method());
        in.put("customerVpa", check.customerVpa());
        in.put("ip", check.ip());
        return toJson(in);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value instanceof List ? "[]" : "{}";
        }
    }
}
