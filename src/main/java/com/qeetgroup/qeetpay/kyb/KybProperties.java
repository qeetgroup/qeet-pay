package com.qeetgroup.qeetpay.kyb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live KYB provider configuration (PRD Module 19). Bound from {@code qeetpay.kyb.*} via
 * {@code @ConfigurationPropertiesScan} on the application class. All values default to empty so the
 * {@link SandboxKybAdapter} is used when {@code enabled=false} (the dev/test default) — no live
 * provider call and, critically, <b>no hardcoded secrets</b> in the codebase.
 *
 * <p>Points at a SurePASS / SignDesk / NSDL / GSTN-style verification API used for PAN, GSTIN and
 * bank penny-drop checks by {@link LiveKybAdapter}.
 *
 * @param enabled   turns the {@link LiveKybAdapter} on (otherwise sandbox)
 * @param baseUrl   provider API root (e.g. {@code https://kyc-api.surepass.io/api/v1})
 * @param apiKey    bearer token / API key issued by the provider (from the environment, never code)
 * @param clientId  optional provider client / tenant id sent as {@code X-Client-Id}
 */
@ConfigurationProperties("qeetpay.kyb")
public record KybProperties(boolean enabled, String baseUrl, String apiKey, String clientId) {

    public KybProperties {
        if (baseUrl == null) baseUrl = "";
        if (apiKey == null) apiKey = "";
        if (clientId == null) clientId = "";
    }
}
