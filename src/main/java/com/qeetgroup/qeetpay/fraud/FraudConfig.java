package com.qeetgroup.qeetpay.fraud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fraud client wiring. */
@Configuration
public class FraudConfig {

    /**
     * Fallback low-level scorer used when the real HTTP client is not active
     * ({@code qeetpay.fraud.enabled=false}, the default in dev/test): every check is allowed. The
     * {@link AiGatewayFraudClient} still wraps this so the allow decision is masked, audited and
     * persisted through the §6.4 gateway like any other.
     */
    @Bean
    @ConditionalOnMissingBean(FraudScorer.class)
    public FraudScorer allowAllFraudScorer() {
        return check -> FraudDecision.allow("fraud scoring disabled");
    }
}
