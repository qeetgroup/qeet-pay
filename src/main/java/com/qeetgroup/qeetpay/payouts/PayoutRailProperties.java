package com.qeetgroup.qeetpay.payouts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live payout-rail (RazorpayX-style) configuration (TAD Module 02). Picked up by
 * {@code @ConfigurationPropertiesScan} on the application class. All values default to empty/sandbox
 * so the {@link SandboxPayoutProvider} path is used when {@code qeetpay.payouts.enabled} is unset
 * (dev/test) — the live {@link RazorpayXPayoutProvider} only activates when {@code enabled=true}.
 *
 * @param enabled       gate for {@link RazorpayXPayoutProvider} (default {@code false} → sandbox)
 * @param baseUrl       payouts API base URL (RazorpayX: {@code https://api.razorpay.com})
 * @param keyId         API key id (HTTP Basic username) — inject via env/secret, never hardcode
 * @param keySecret     API key secret (HTTP Basic password) — inject via env/secret, never hardcode
 * @param accountNumber the source RazorpayX account number the payout is debited from
 * @param purpose       RazorpayX payout purpose (e.g. {@code payout}, {@code vendor bill})
 */
@ConfigurationProperties("qeetpay.payouts")
public record PayoutRailProperties(
        boolean enabled,
        String baseUrl,
        String keyId,
        String keySecret,
        String accountNumber,
        String purpose) {

    public PayoutRailProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.razorpay.com";
        if (keyId == null) keyId = "";
        if (keySecret == null) keySecret = "";
        if (accountNumber == null) accountNumber = "";
        if (purpose == null || purpose.isBlank()) purpose = "payout";
    }
}
