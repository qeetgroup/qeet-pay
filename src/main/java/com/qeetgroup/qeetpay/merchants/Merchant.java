package com.qeetgroup.qeetpay.merchants;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A merchant — the unit of multi-tenancy in Qeet Pay (TAD §6.1). The {@code platform.merchants}
 * table is the tenant registry itself, so it is <em>not</em> RLS-scoped; merchant-owned tables
 * (accounts, journal_*) are.
 */
@Entity
@Table(name = "merchants", schema = "platform")
public class Merchant {

    @Id private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Merchant() {}

    public Merchant(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
