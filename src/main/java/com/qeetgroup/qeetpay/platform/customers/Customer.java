package com.qeetgroup.qeetpay.platform.customers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Merchant-scoped customer record — canonical handle used across billing and mandates. */
@Entity
@Table(name = "customers", schema = "platform")
public class Customer {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String ref; // caller-supplied opaque key (email / user-id)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Customer() {}

    public Customer(UUID merchantId, String ref) {
        this.merchantId = merchantId;
        this.ref = ref;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getRef() { return ref; }
}
