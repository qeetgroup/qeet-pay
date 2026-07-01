package com.qeetgroup.qeetpay.payments;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Razorpay webhook events (TAD §7.1). The endpoint is public (Razorpay has no merchant
 * auth token); authenticity is verified via HMAC-SHA256 signature on the raw body.
 *
 * <p>Phase 1: logs events and returns 200 OK. Full capture-on-webhook is wired in M05 (webhook
 * engine) once the merchant lookup from order notes is in place.
 */
@RestController
@RequestMapping("/v1/payments/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayProperties razorpayProps;

    public RazorpayWebhookController(RazorpayProperties razorpayProps) {
        this.razorpayProps = razorpayProps;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (!razorpayProps.enabled()) {
            return ResponseEntity.ok().build();
        }
        if (signature == null || !verifySignature(rawBody, signature)) {
            log.warn("Razorpay webhook: invalid or missing signature — ignoring");
            return ResponseEntity.status(400).build();
        }
        log.info("Razorpay webhook received — event processing wired in M05");
        return ResponseEntity.ok().build();
    }

    private boolean verifySignature(String body, String receivedSig) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    razorpayProps.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return expected.equals(receivedSig);
        } catch (Exception e) {
            log.error("Razorpay webhook signature verification failed", e);
            return false;
        }
    }
}
