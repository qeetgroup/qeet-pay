package com.qeetgroup.qeetpay.platform.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A merchant-scoped API key (TAD §10.1). Only the SHA-256 hash of the raw {@code qp_live_…} /
 * {@code qp_test_…} secret is stored; the raw value is shown to the caller exactly once at mint.
 */
@Entity
@Table(name = "api_keys", schema = "platform")
public class ApiKey {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    /** Space-separated scopes, e.g. {@code "pay:admin pay:developer"}. */
    @Column(nullable = false)
    private String scopes;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ApiKey() {}

    public ApiKey(UUID merchantId, String keyHash, String keyPrefix, String scopes) {
        this.merchantId = merchantId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.scopes = scopes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getScopes() {
        return scopes;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
