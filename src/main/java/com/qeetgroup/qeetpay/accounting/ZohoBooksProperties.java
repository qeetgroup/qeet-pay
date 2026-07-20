package com.qeetgroup.qeetpay.accounting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Zoho Books connection config (bound from {@code qeetpay.accounting.zoho.*}, picked up by
 * {@code @ConfigurationPropertiesScan}). All values default to empty / disabled so the sandbox
 * connector is used when no creds are set (dev/test). {@code baseUrl} defaults to the India DC.
 */
@ConfigurationProperties("qeetpay.accounting.zoho")
public record ZohoBooksProperties(
        boolean enabled,
        String baseUrl,
        String organizationId,
        String accessToken) {

    public ZohoBooksProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://www.zohoapis.in/books/v3";
        if (organizationId == null) organizationId = "";
        if (accessToken == null) accessToken = "";
    }
}
