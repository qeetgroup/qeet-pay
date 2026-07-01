package com.qeetgroup.qeetpay.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A subscription plan. Supports FLAT, PER_UNIT, TIERED, VOLUME, and HYBRID pricing models. */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false)
    private PricingModel pricingModel = PricingModel.FLAT;

    /** JSON tiers array for TIERED/VOLUME/HYBRID: [{upTo:N,unitPrice:M},...,{upTo:null,unitPrice:K}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String tiers;

    @Column(name = "usage_metric_key")
    private String usageMetricKey;

    @Column(name = "trial_days", nullable = false)
    private int trialDays = 0;

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

    public Plan withPricingModel(PricingModel model, String tiersJson, String metricKey) {
        this.pricingModel = model;
        this.tiers = tiersJson;
        this.usageMetricKey = metricKey;
        return this;
    }

    public Plan withTrialDays(int days) {
        this.trialDays = days;
        return this;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCode() { return code; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public BillingInterval getInterval() { return interval; }
    public PricingModel getPricingModel() { return pricingModel; }
    public String getTiers() { return tiers; }
    public String getUsageMetricKey() { return usageMetricKey; }
    public int getTrialDays() { return trialDays; }
}
