package com.qeetgroup.qeetpay.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An inbound WhatsApp message received from a customer (PRD Module 09.2/09.3). Append-only and
 * merchant-scoped; idempotent on the provider (Meta/WhatsApp) message id so a replayed webhook is
 * stored once.
 */
@Entity
@Table(name = "inbound_messages", schema = "messaging")
public class InboundMessage {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "provider_message_id", nullable = false)
    private String providerMessageId;

    @Column(name = "wa_from", nullable = false)
    private String waFrom;

    @Column private String body;

    @Column(name = "parsed_command")
    private String parsedCommand;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected InboundMessage() {}

    public InboundMessage(
            UUID merchantId, String providerMessageId, String waFrom, String body, String parsedCommand) {
        this.merchantId = merchantId;
        this.providerMessageId = providerMessageId;
        this.waFrom = waFrom;
        this.body = body;
        this.parsedCommand = parsedCommand;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getWaFrom() { return waFrom; }
    public String getBody() { return body; }
    public String getParsedCommand() { return parsedCommand; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
