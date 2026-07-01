package com.qeetgroup.qeetpay.webhooks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Merchant-registered outbound webhook endpoint with HMAC signing secret. */
@Entity
@Table(name = "endpoints", schema = "webhooks")
public class WebhookEndpoint {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String events = "[\"*\"]";

    @Column(name = "signing_secret", nullable = false)
    private String signingSecret;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WebhookEndpoint() {}

    public WebhookEndpoint(UUID merchantId, String url, String events, String signingSecret) {
        this.merchantId = merchantId;
        this.url = url;
        this.events = events != null ? events : "[\"*\"]";
        this.signingSecret = signingSecret;
    }

    public void disable() { this.status = "DISABLED"; }

    public boolean isActive() { return "ACTIVE".equals(status); }

    /** True if this endpoint subscribes to all events or specifically to the given event type. */
    public boolean subscribesTo(String eventType) {
        return events.contains("\"*\"") || events.contains("\"" + eventType + "\"");
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getUrl() { return url; }
    public String getEvents() { return events; }
    public String getSigningSecret() { return signingSecret; }
    public String getStatus() { return status; }
}
