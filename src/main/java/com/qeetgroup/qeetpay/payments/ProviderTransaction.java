package com.qeetgroup.qeetpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable audit record of a single provider API call (TAD §7.1 orchestration). */
@Entity
@Table(name = "provider_transactions", schema = "payments")
public class ProviderTransaction {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(nullable = false)
    private String operation; // AUTHORIZE | CAPTURE | REFUND

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ProviderTransaction() {}

    public ProviderTransaction(
            UUID paymentId,
            UUID merchantId,
            String providerName,
            String operation,
            String providerRef,
            boolean success,
            String rawResponse) {
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.providerName = providerName;
        this.operation = operation;
        this.providerRef = providerRef;
        this.success = success;
        this.rawResponse = rawResponse;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getOperation() {
        return operation;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public boolean isSuccess() {
        return success;
    }
}
