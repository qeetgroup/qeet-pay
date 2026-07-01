package com.qeetgroup.qeetpay.mandates;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A recurring payment authorization — UPI AutoPay or NACH (TAD Module 02). */
@Entity
@Table(name = "mandates", schema = "mandates")
public class Mandate {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MandateType type;

    @Column(name = "limit_minor", nullable = false)
    private long limitMinor;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MandateFrequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MandateStatus status = MandateStatus.CREATED;

    @Column(name = "provider_mandate_id")
    private String providerMandateId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Mandate() {}

    public Mandate(
            UUID merchantId,
            UUID customerId,
            MandateType type,
            long limitMinor,
            String currency,
            MandateFrequency frequency,
            LocalDate startDate,
            LocalDate endDate) {
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.type = type;
        this.limitMinor = limitMinor;
        this.currency = currency;
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void activate(String providerMandateId) {
        this.status = MandateStatus.ACTIVE;
        this.providerMandateId = providerMandateId;
        touch();
    }

    public void pause() {
        if (this.status != MandateStatus.ACTIVE) {
            throw new IllegalStateException("can only pause an ACTIVE mandate; status=" + status);
        }
        this.status = MandateStatus.PAUSED;
        touch();
    }

    public void revoke() {
        if (this.status == MandateStatus.REVOKED) return;
        this.status = MandateStatus.REVOKED;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getCustomerId() { return customerId; }
    public MandateType getType() { return type; }
    public long getLimitMinor() { return limitMinor; }
    public String getCurrency() { return currency; }
    public MandateFrequency getFrequency() { return frequency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public MandateStatus getStatus() { return status; }
    public String getProviderMandateId() { return providerMandateId; }
}
