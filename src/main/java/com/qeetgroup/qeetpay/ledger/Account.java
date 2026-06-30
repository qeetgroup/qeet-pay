package com.qeetgroup.qeetpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A merchant-scoped ledger account (a line in the chart of accounts). */
@Entity
@Table(name = "accounts", schema = "ledger")
public class Account {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {}

    public Account(UUID merchantId, String code, String name, AccountType type, String currency) {
        this.merchantId = merchantId;
        this.code = code;
        this.name = name;
        this.type = type;
        this.currency = currency;
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

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }
}
