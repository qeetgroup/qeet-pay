package com.qeetgroup.qeetpay.accounting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SAP Business One connection config (bound from {@code qeetpay.accounting.sap.*}, picked up by
 * {@code @ConfigurationPropertiesScan}). All values default to empty / disabled so no live SAP
 * connector is wired unless creds are set (dev/test). {@code baseUrl} points at the SAP Business One
 * <b>Service Layer</b> root (default = the standard local Service Layer endpoint); the connector
 * logs in with {@code companyDb}/{@code username}/{@code password} then posts journal entries.
 */
@ConfigurationProperties("qeetpay.accounting.sap")
public record SapProperties(
        boolean enabled,
        String baseUrl,
        String companyDb,
        String username,
        String password) {

    public SapProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://localhost:50000/b1s/v1";
        if (companyDb == null) companyDb = "";
        if (username == null) username = "";
        if (password == null) password = "";
    }
}
