package com.qeetgroup.qeetpay.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-(merchant, phone) conversational state for the WhatsApp subscription bot (PRD Module 09.3).
 * Tracks the last command, a free-form context reference (e.g. the subscription / pay-collection the
 * conversation is about), and a running message count. Merchant-scoped; one row per phone.
 */
@Entity
@Table(name = "whatsapp_sessions", schema = "messaging")
public class WhatsAppSession {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "wa_phone", nullable = false)
    private String waPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WhatsAppSessionState state = WhatsAppSessionState.IDLE;

    @Column(name = "last_command")
    private String lastCommand;

    @Column(name = "context_ref")
    private String contextRef;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WhatsAppSession() {}

    public WhatsAppSession(UUID merchantId, String waPhone) {
        this.merchantId = merchantId;
        this.waPhone = waPhone;
    }

    /** Advances the session after handling one inbound command. */
    public void recordCommand(BotCommand command, WhatsAppSessionState state, String contextRef) {
        this.lastCommand = command.name();
        this.state = state;
        this.contextRef = contextRef;
        this.messageCount += 1;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getWaPhone() { return waPhone; }
    public WhatsAppSessionState getState() { return state; }
    public String getLastCommand() { return lastCommand; }
    public String getContextRef() { return contextRef; }
    public int getMessageCount() { return messageCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
