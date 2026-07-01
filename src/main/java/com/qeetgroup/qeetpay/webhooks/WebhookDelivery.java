package com.qeetgroup.qeetpay.webhooks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Delivery record for one outbound webhook attempt. Mutable: status, attempt_count, response. */
@Entity
@Table(name = "deliveries", schema = "webhooks")
public class WebhookDelivery {

    public static final String PENDING   = "PENDING";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED    = "FAILED";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(nullable = false)
    private String status = PENDING;

    @Column(name = "last_response_code")
    private Integer lastResponseCode;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected WebhookDelivery() {}

    public WebhookDelivery(UUID endpointId, UUID merchantId, String eventType, String payload) {
        this.endpointId = endpointId;
        this.merchantId = merchantId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void recordSuccess(int responseCode) {
        this.attemptCount++;
        this.status = DELIVERED;
        this.lastResponseCode = responseCode;
        this.deliveredAt = Instant.now();
    }

    public void recordFailure(int responseCode, String error) {
        this.attemptCount++;
        this.status = FAILED;
        this.lastResponseCode = responseCode;
        this.lastError = error;
    }

    public void recordNetworkError(String error) {
        this.attemptCount++;
        this.status = FAILED;
        this.lastError = error;
    }

    public UUID getId() { return id; }
    public UUID getEndpointId() { return endpointId; }
    public UUID getMerchantId() { return merchantId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttemptCount() { return attemptCount; }
    public String getStatus() { return status; }
    public Integer getLastResponseCode() { return lastResponseCode; }
    public String getLastError() { return lastError; }
    public Instant getDeliveredAt() { return deliveredAt; }
}
