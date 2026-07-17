package com.qeetgroup.qeetpay.virtualaccounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A virtual account assigned to one of a merchant's customers for auto-reconciled collection. */
@Entity
@Table(name = "virtual_accounts", schema = "virtualaccounts")
public class VirtualAccount {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "va_number", nullable = false)
    private String vaNumber;

    @Column(nullable = false)
    private String ifsc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected VirtualAccount() {}

    public VirtualAccount(UUID merchantId, String customerRef, String vaNumber, String ifsc) {
        this.merchantId = merchantId;
        this.customerRef = customerRef;
        this.vaNumber = vaNumber;
        this.ifsc = ifsc;
    }

    public void close() {
        if (status == VirtualAccountStatus.CLOSED) {
            throw new IllegalStateException("virtual account already closed");
        }
        this.status = VirtualAccountStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public boolean isActive() {
        return status == VirtualAccountStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCustomerRef() { return customerRef; }
    public String getVaNumber() { return vaNumber; }
    public String getIfsc() { return ifsc; }
    public VirtualAccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
