package com.qeetgroup.qeetpay.filing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live GSTN filing configuration (TAD §7.4), bound from {@code qeetpay.gstn.*} via
 * {@code @ConfigurationPropertiesScan}. Enables the real {@link LiveGstnFilingAdapter} (GSTN via an
 * ASP/GSP such as ClearTax) when {@code qeetpay.gstn.enabled=true}; otherwise the deterministic
 * {@link SandboxGstnFilingAdapter} is used. All secrets come from config/env — never hardcoded — and
 * default to empty so the sandbox path is the safe default in dev/test.
 *
 * <p>{@code gstin} is the filer's registered GSTIN (the credential the GSP files under). Return
 * endpoints are built as {@code {returnsPath}/{gstr1|gstr3b}/{save|file}} against {@code baseUrl}.
 */
@ConfigurationProperties("qeetpay.gstn")
public record GstnProperties(
        boolean enabled,
        String baseUrl,
        String clientId,
        String clientSecret,
        String username,
        String password,
        String gstin,
        String authPath,
        String returnsPath,
        long tokenTtlSeconds) {

    public GstnProperties {
        if (baseUrl == null) baseUrl = "";
        if (clientId == null) clientId = "";
        if (clientSecret == null) clientSecret = "";
        if (username == null) username = "";
        if (password == null) password = "";
        if (gstin == null) gstin = "";
        if (authPath == null || authPath.isBlank()) authPath = "/authenticate";
        if (returnsPath == null || returnsPath.isBlank()) returnsPath = "/returns";
        if (tokenTtlSeconds <= 0) tokenTtlSeconds = 21_600;
    }
}
