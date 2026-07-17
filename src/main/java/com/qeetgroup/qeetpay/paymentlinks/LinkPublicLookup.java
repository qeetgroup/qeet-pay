package com.qeetgroup.qeetpay.paymentlinks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Public routing map for the hosted-checkout surface: a link share {@code code} → its owning
 * {@code merchantId} and {@code linkId}. Payment-link codes are stored {@code UNIQUE(merchant_id, code)}
 * (not globally), so a PUBLIC "GET by code" — where no merchant context exists yet — cannot resolve a
 * code on its own. This table closes that gap: it is written alongside every link and, because it holds
 * no tenant-private data and must be readable before a merchant is known, it carries <em>no</em> row-level
 * security (see {@code V36__checkout_link_lookup.sql}). It intentionally exposes nothing beyond the
 * routing keys — no amount, title, status, payment id or reference.
 */
@Entity
@Table(name = "link_public_lookup", schema = "paymentlinks")
public class LinkPublicLookup {

    @Id
    @Column(nullable = false)
    private String code;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LinkPublicLookup() {}

    public LinkPublicLookup(String code, UUID merchantId, UUID linkId) {
        this.code = code;
        this.merchantId = merchantId;
        this.linkId = linkId;
    }

    public String getCode() {
        return code;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public UUID getLinkId() {
        return linkId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
