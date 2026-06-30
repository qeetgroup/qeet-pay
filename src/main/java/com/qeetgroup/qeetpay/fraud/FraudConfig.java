package com.qeetgroup.qeetpay.fraud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Fraud client wiring. */
@Configuration
public class FraudConfig {

    /**
     * Fallback used when the real HTTP client is not active ({@code qeetpay.fraud.enabled=false},
     * the default in dev/test): every check is allowed.
     */
    @Bean
    @ConditionalOnMissingBean(FraudClient.class)
    public FraudClient allowAllFraudClient() {
        return check -> FraudDecision.allow("fraud scoring disabled");
    }
}
