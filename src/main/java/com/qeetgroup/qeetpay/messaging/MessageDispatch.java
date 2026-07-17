package com.qeetgroup.qeetpay.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A rendered message queued for delivery via qeet-notify; SENT/FAILED on the delivery callback. */
@Entity
@Table(name = "message_dispatches", schema = "messaging")
public class MessageDispatch {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "template_key", nullable = false)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageChannel channel;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "rendered_body", nullable = false)
    private String renderedBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispatchStatus status = DispatchStatus.QUEUED;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "related_ref")
    private String relatedRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected MessageDispatch() {}

    public MessageDispatch(
            UUID merchantId, String templateKey, MessageChannel channel, String recipient,
            String renderedBody, String relatedRef) {
        this.merchantId = merchantId;
        this.templateKey = templateKey;
        this.channel = channel;
        this.recipient = recipient;
        this.renderedBody = renderedBody;
        this.relatedRef = relatedRef;
    }

    public void markSent(String providerRef) {
        if (status == DispatchStatus.SENT) {
            return; // idempotent
        }
        this.status = DispatchStatus.SENT;
        this.providerRef = providerRef;
        this.sentAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = DispatchStatus.FAILED;
        this.failureReason = reason;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getTemplateKey() { return templateKey; }
    public MessageChannel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getRenderedBody() { return renderedBody; }
    public DispatchStatus getStatus() { return status; }
    public String getProviderRef() { return providerRef; }
    public String getRelatedRef() { return relatedRef; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
