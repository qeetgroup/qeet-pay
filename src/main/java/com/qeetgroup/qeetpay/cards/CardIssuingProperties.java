package com.qeetgroup.qeetpay.cards;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live card-issuing rail configuration (PRD Module 10.3). Bound from {@code qeetpay.cards.*} via
 * {@code @ConfigurationPropertiesScan} on the application class. All values default to empty so the
 * {@link SandboxCardIssuingProvider} remains the default when {@code enabled=false} (the dev/test
 * default) — no live provider call and, critically, <b>no hardcoded secrets</b> in the codebase.
 *
 * <p>Points at an M2P / Decentro-style card-issuing API used by {@link LiveCardIssuingProvider}.
 *
 * @param enabled   turns the {@link LiveCardIssuingProvider} on (otherwise sandbox)
 * @param baseUrl   issuing API root (e.g. {@code https://in.staging.decentro.tech/v2})
 * @param apiKey    API key issued by the provider, sent as {@code X-Api-Key} (from env, never code)
 * @param programId the card-program id under which cards are issued
 */
@ConfigurationProperties("qeetpay.cards")
public record CardIssuingProperties(boolean enabled, String baseUrl, String apiKey, String programId) {

    public CardIssuingProperties {
        if (baseUrl == null) baseUrl = "";
        if (apiKey == null) apiKey = "";
        if (programId == null) programId = "";
    }
}
