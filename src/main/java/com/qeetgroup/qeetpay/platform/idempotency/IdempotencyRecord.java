package com.qeetgroup.qeetpay.platform.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the outcome of a mutating request keyed by ({@code merchant_id}, {@code Idempotency-Key})
 * so a retry returns the original result instead of re-executing (TAD §4.3).
 */
@Entity
@Table(name = "idempotency_keys", schema = "platform")
public class IdempotencyRecord {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "idem_key", nullable = false)
    private String idemKey;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdempotencyRecord() {}

    public IdempotencyRecord(UUID merchantId, String idemKey, int responseStatus, String responseBody) {
        this.merchantId = merchantId;
        this.idemKey = idemKey;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
