package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An on-device UPI Lite wallet for low-value payments. Holds a {@code balance_minor} that mutates as
 * value is topped up and spent. Per-transaction and per-day spend limits are enforced by the service.
 */
@Entity
@Table(name = "upi_lite_wallets", schema = "offline")
public class UpiLiteWallet {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor = 0;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpiLiteWalletStatus status = UpiLiteWalletStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected UpiLiteWallet() {}

    public UpiLiteWallet(UUID merchantId, String customerRef, String currency) {
        this.merchantId = merchantId;
        this.customerRef = customerRef;
        this.currency = currency;
    }

    public void topUp(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("top-up amount must be positive");
        }
        this.balanceMinor += amountMinor;
    }

    public void spend(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("spend amount must be positive");
        }
        if (amountMinor > balanceMinor) {
            throw new IllegalArgumentException("insufficient UPI Lite balance");
        }
        this.balanceMinor -= amountMinor;
    }

    public void close() {
        if (status == UpiLiteWalletStatus.CLOSED) {
            throw new IllegalStateException("wallet already closed");
        }
        this.status = UpiLiteWalletStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public boolean isActive() {
        return status == UpiLiteWalletStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCustomerRef() { return customerRef; }
    public long getBalanceMinor() { return balanceMinor; }
    public String getCurrency() { return currency; }
    public UpiLiteWalletStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
