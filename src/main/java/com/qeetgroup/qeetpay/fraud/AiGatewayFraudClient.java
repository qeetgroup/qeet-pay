package com.qeetgroup.qeetpay.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The {@link FraudClient} payments consult. It routes the underlying {@link FraudScorer} through the
 * {@code ai/AiGateway} §6.4 safety substrate (PII masking + audit + outbox) and persists a
 * {@link FraudDecisionRecord}, via {@link FraudGatewayAuditor}.
 *
 * <p><b>Fail-open.</b> Scoring is advisory and must never block a payment: any failure in the gateway
 * or the audit persistence is caught here, and the client falls back to the deterministic scorer
 * directly (and, in the worst case, to ALLOW). The audit work runs in its own transaction inside the
 * auditor, so a rollback there does not affect the payment transaction.
 */
@Component
public class AiGatewayFraudClient implements FraudClient {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayFraudClient.class);

    private final FraudGatewayAuditor auditor;
    private final FraudScorer delegate;

    public AiGatewayFraudClient(FraudGatewayAuditor auditor, FraudScorer delegate) {
        this.auditor = auditor;
        this.delegate = delegate;
    }

    @Override
    public FraudDecision score(FraudCheck check) {
        try {
            return auditor.scoreThroughGateway(check);
        } catch (RuntimeException e) {
            log.warn("fraud scoring via AI gateway failed; failing open to the deterministic scorer", e);
            try {
                return delegate.score(check);
            } catch (RuntimeException inner) {
                return FraudDecision.allow("fraud scoring unavailable");
            }
        }
    }
}
