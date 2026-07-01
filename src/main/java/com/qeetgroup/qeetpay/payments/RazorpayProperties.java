package com.qeetgroup.qeetpay.payments;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Razorpay provider configuration (TAD §7.1). Picked up by {@code @ConfigurationPropertiesScan}
 * on the application class. All values default to empty so the sandbox path is used when keys are
 * absent (dev/test).
 */
@ConfigurationProperties("qeetpay.razorpay")
public record RazorpayProperties(
        boolean enabled,
        String keyId,
        String keySecret,
        String webhookSecret) {

    public RazorpayProperties {
        if (keyId == null) keyId = "";
        if (keySecret == null) keySecret = "";
        if (webhookSecret == null) webhookSecret = "";
    }
}
