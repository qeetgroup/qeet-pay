package com.qeetgroup.qeetpay.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-merchant, per-target connection settings. Holds the generic-webhook endpoint and the Zoho
 * per-merchant organization id; live Zoho credentials (access token, base URL) come from app config
 * ({@code qeetpay.accounting.zoho.*}), never persisted here. One row per (merchant, target).
 */
@Entity
@Table(name = "accounting_connections", schema = "accounting")
public class AccountingConnection {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountingTarget target;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "zoho_organization_id")
    private String zohoOrganizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AccountingConnection() {}

    public AccountingConnection(UUID merchantId, AccountingTarget target) {
        this.merchantId = merchantId;
        this.target = target;
    }

    public void update(boolean enabled, String webhookUrl, String zohoOrganizationId) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl;
        this.zohoOrganizationId = zohoOrganizationId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public AccountingTarget getTarget() { return target; }
    public boolean isEnabled() { return enabled; }
    public String getWebhookUrl() { return webhookUrl; }
    public String getZohoOrganizationId() { return zohoOrganizationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
