package com.qeetgroup.qeetpay.webhooks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure unit test — no Spring context. */
class WebhookSignatureTest {

    @Test
    void computeAndVerify_roundtrip() {
        String payload = "{\"event\":\"payment.captured\",\"amount\":5000}";
        String secret = "whsec_test_secret";

        String signature = WebhookSignature.compute(payload, secret);

        assertThat(signature).startsWith("sha256=");
        assertThat(signature).hasSize(71); // "sha256=" (7) + 64 hex chars
        assertThat(WebhookSignature.verify(payload, secret, signature)).isTrue();
    }

    @Test
    void differentPayloadProducesDifferentSignature() {
        String s1 = WebhookSignature.compute("{\"a\":1}", "secret");
        String s2 = WebhookSignature.compute("{\"a\":2}", "secret");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentSecretProducesDifferentSignature() {
        String payload = "{\"event\":\"test\"}";
        String s1 = WebhookSignature.compute(payload, "secret1");
        String s2 = WebhookSignature.compute(payload, "secret2");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void verifyReturnsFalseForWrongSignature() {
        String payload = "{\"event\":\"test\"}";
        assertThat(WebhookSignature.verify(payload, "secret", "sha256=wrongsig")).isFalse();
    }
}
