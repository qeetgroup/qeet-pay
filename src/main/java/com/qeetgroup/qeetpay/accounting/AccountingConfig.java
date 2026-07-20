package com.qeetgroup.qeetpay.accounting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the credential-gated ZOHO connector and its sandbox fallback. Declaring both as ordered
 * {@code @Bean} methods in one {@code @Configuration} makes the {@code @ConditionalOnMissingBean}
 * evaluation deterministic (it reliably sees the earlier-declared {@code zohoBooksConnector}),
 * unlike two independently component-scanned beans. {@link TallyXmlConnector} and
 * {@link WebhookConnector} need no creds and stay plain {@code @Component}s.
 */
@Configuration
public class AccountingConfig {

    /** Live Zoho Books connector — only when creds are configured. */
    @Bean
    @ConditionalOnProperty(prefix = "qeetpay.accounting.zoho", name = "enabled", havingValue = "true")
    public ZohoBooksConnector zohoBooksConnector(ZohoBooksProperties props, ObjectMapper objectMapper) {
        return new ZohoBooksConnector(props, objectMapper);
    }

    /** Sandbox stand-in for the ZOHO target when no live Zoho connector is present. */
    @Bean
    @ConditionalOnMissingBean(ZohoBooksConnector.class)
    public SandboxAccountingConnector sandboxAccountingConnector(ObjectMapper objectMapper) {
        return new SandboxAccountingConnector(objectMapper);
    }
}
