package com.qeetgroup.qeetpay.gst;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live IRP (Invoice Registration Portal) configuration (TAD §7.3), bound from {@code qeetpay.irp.*}
 * via {@code @ConfigurationPropertiesScan}. Enables the real {@link LiveIrpAdapter} (NIC-IRP / a GSP
 * such as ClearTax) when {@code qeetpay.irp.enabled=true}; otherwise the deterministic
 * {@link SandboxIrpAdapter} is used. All secrets come from config/env — never hardcoded — and default
 * to empty so the sandbox path is the safe default in dev/test.
 *
 * <p>Endpoint paths default to the NIC IRP e-invoice API shape; a GSP that re-hosts them can override.
 */
@ConfigurationProperties("qeetpay.irp")
public record IrpProperties(
        boolean enabled,
        String baseUrl,
        String clientId,
        String clientSecret,
        String username,
        String password,
        String authPath,
        String generatePath,
        String cancelPath,
        long tokenTtlSeconds) {

    public IrpProperties {
        if (baseUrl == null) baseUrl = "";
        if (clientId == null) clientId = "";
        if (clientSecret == null) clientSecret = "";
        if (username == null) username = "";
        if (password == null) password = "";
        if (authPath == null || authPath.isBlank()) authPath = "/eivital/v1.04/auth";
        if (generatePath == null || generatePath.isBlank()) generatePath = "/eicore/v1.03/invoice";
        if (cancelPath == null || cancelPath.isBlank()) cancelPath = "/eicore/v1.03/invoice/cancel";
        // NIC auth tokens are valid ~6h; used only as a fallback when the auth response omits an expiry.
        if (tokenTtlSeconds <= 0) tokenTtlSeconds = 21_600;
    }
}
