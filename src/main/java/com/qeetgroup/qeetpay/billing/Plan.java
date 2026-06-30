package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A subscription plan (flat-amount pricing for the Phase-1 slice). */
@Entity
@Table(name = "plans", schema = "billing")
public class Plan {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval", nullable = false)
    private BillingInterval interval;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Plan() {}

    public Plan(UUID merchantId, String code, String name, long amountMinor, String currency, BillingInterval interval) {
        this.merchantId = merchantId;
        this.code = code;
        this.name = name;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.interval = interval;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getCode() {
        return code;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public BillingInterval getInterval() {
        return interval;
    }
}
