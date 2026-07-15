package com.qeetgroup.qeetpay.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A merchant-configured message template (per key + channel) with {@code {{placeholder}}} variables. */
@Entity
@Table(name = "message_templates", schema = "messaging")
public class MessageTemplate {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageChannel channel;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MessageTemplate() {}

    public MessageTemplate(UUID merchantId, String templateKey, MessageChannel channel, String body) {
        this.merchantId = merchantId;
        this.templateKey = templateKey;
        this.channel = channel;
        this.body = body;
    }

    public void update(String body, boolean active) {
        this.body = body;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getTemplateKey() { return templateKey; }
    public MessageChannel getChannel() { return channel; }
    public String getBody() { return body; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
