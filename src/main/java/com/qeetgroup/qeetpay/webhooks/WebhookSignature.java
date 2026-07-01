package com.qeetgroup.qeetpay.webhooks;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** HMAC-SHA256 signature computation for outbound webhook payloads. */
public final class WebhookSignature {

    private WebhookSignature() {}

    /**
     * Computes {@code sha256=<hex>} for the given payload and signing secret.
     * The value is sent as the {@code X-QeetPay-Signature} request header.
     */
    public static String compute(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    public static boolean verify(String payload, String secret, String signature) {
        return compute(payload, secret).equals(signature);
    }
}
