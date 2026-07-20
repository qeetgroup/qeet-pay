package com.qeetgroup.qeetpay.payments;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * auth token); authenticity is verified via HMAC-SHA256 signature on the raw body, then the event is
 * processed into the domain by {@link RazorpayWebhookService}.
 *
 * <p>Processing is idempotent on the Razorpay event id ({@code x-razorpay-event-id}; a SHA-256 of the
 * body when absent), so redelivered events are acked without re-applying. Bad or missing signatures
 * are rejected with 400; every other outcome — handled, unknown, or already-seen — returns 200, as
 * Razorpay retries on any non-2xx.
 */
@Tag(
        name = "Payments",
        description = "Inbound Razorpay webhook receiver — authenticity verified via HMAC-SHA256 on the raw body.")
@RestController
@RequestMapping("/v1/payments/razorpay")
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final RazorpayProperties razorpayProps;
    private final RazorpayWebhookService webhookService;

    public RazorpayWebhookController(
            RazorpayProperties razorpayProps, RazorpayWebhookService webhookService) {
        this.razorpayProps = razorpayProps;
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        if (!razorpayProps.enabled()) {
            return ResponseEntity.ok().build();
        }
        if (signature == null || !verifySignature(rawBody, signature)) {
            log.warn("Razorpay webhook: invalid or missing signature — rejecting");
            return ResponseEntity.status(400).build();
        }
        String resolvedEventId =
                (eventId != null && !eventId.isBlank()) ? eventId : sha256Hex(rawBody);
        webhookService.process(rawBody, resolvedEventId);
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

    /** Stable de-dup id when Razorpay omits {@code x-razorpay-event-id}: a hash of the raw body. */
    private static String sha256Hex(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
