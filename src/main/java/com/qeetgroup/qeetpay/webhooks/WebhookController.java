package com.qeetgroup.qeetpay.webhooks;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Webhook API (TAD Module 05): register endpoints, view delivery history. */
@Tag(
        name = "Webhooks",
        description = "Register webhook endpoints (event subscriptions + signing secret) and view delivery history.")
@RestController
@RequestMapping("/v1/webhooks")
public class WebhookController {

    private final WebhookDeliveryService webhooks;

    public WebhookController(WebhookDeliveryService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/endpoints")
    public ResponseEntity<EndpointView> register(@Valid @RequestBody RegisterRequest req) {
        WebhookEndpoint ep = webhooks.register(
                MerchantContext.require(), req.url(), req.events(), req.signingSecret());
        return ResponseEntity.status(HttpStatus.CREATED).body(EndpointView.of(ep));
    }

    @GetMapping("/endpoints")
    public List<EndpointView> list() {
        return webhooks.listEndpoints(MerchantContext.require()).stream().map(EndpointView::of).toList();
    }

    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<Void> disable(@PathVariable UUID id) {
        webhooks.disable(MerchantContext.require(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/endpoints/{id}/deliveries")
    public List<DeliveryView> deliveries(@PathVariable UUID id) {
        return webhooks.deliveriesFor(MerchantContext.require(), id).stream().map(DeliveryView::of).toList();
    }

    // ── Records ──────────────────────────────────────────────────────────────

    public record RegisterRequest(@NotBlank String url, String events, @NotBlank String signingSecret) {}

    public record EndpointView(String id, String url, String events, String status) {
        static EndpointView of(WebhookEndpoint e) {
            return new EndpointView(e.getId().toString(), e.getUrl(), e.getEvents(), e.getStatus());
        }
    }

    public record DeliveryView(String id, String eventType, String status, int attemptCount,
            Integer lastResponseCode, String lastError, Instant deliveredAt) {
        static DeliveryView of(WebhookDelivery d) {
            return new DeliveryView(d.getId().toString(), d.getEventType(), d.getStatus(),
                    d.getAttemptCount(), d.getLastResponseCode(), d.getLastError(), d.getDeliveredAt());
        }
    }
}
