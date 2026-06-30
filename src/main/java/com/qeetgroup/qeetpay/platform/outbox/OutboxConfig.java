package com.qeetgroup.qeetpay.platform.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Outbox publisher wiring. */
@Configuration
public class OutboxConfig {

    private static final Logger log = LoggerFactory.getLogger(OutboxConfig.class);

    /**
     * Fallback {@link NatsPublisher} used whenever the real JetStream publisher is not active
     * ({@code qeetpay.nats.enabled=false}, the default in dev/test). Logs instead of sending, so the
     * outbox foundation works end-to-end without a live NATS.
     */
    @Bean
    @ConditionalOnMissingBean(NatsPublisher.class)
    public NatsPublisher loggingNatsPublisher() {
        return (subject, payload) ->
                log.debug("[outbox] NATS disabled — would publish to {}: {}", subject, payload);
    }
}
