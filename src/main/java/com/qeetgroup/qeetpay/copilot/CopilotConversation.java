package com.qeetgroup.qeetpay.copilot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A copilot conversation thread, bound to one {@link CopilotSurface} and scoped to a merchant (RLS).
 * Mutable only in {@code updatedAt} (bumped as turns are appended); its messages are append-only.
 */
@Entity
@Table(name = "copilot_conversations", schema = "copilot")
public class CopilotConversation {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CopilotSurface surface;

    @Column private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CopilotConversation() {}

    public CopilotConversation(UUID merchantId, CopilotSurface surface, String title) {
        this.merchantId = merchantId;
        this.surface = surface;
        this.title = title;
    }

    /** Bumps {@code updatedAt} — call before persisting after appending a turn. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public CopilotSurface getSurface() {
        return surface;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
