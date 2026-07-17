package com.qeetgroup.qeetpay.cards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A prepaid virtual card (expense or wallet) funded from and spent back to the merchant's balance. */
@Entity
@Table(name = "cards", schema = "cards")
public class VirtualCard {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "holder_ref", nullable = false)
    private String holderRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType type;

    @Column(name = "masked_pan", nullable = false)
    private String maskedPan;

    @Column(nullable = false)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    protected VirtualCard() {}

    public VirtualCard(UUID merchantId, String holderRef, CardType type, String maskedPan, String currency) {
        this.merchantId = merchantId;
        this.holderRef = holderRef;
        this.type = type;
        this.maskedPan = maskedPan;
        this.currency = currency;
    }

    public boolean isActive() {
        return status == CardStatus.ACTIVE;
    }

    /** Adds funds to the card balance. */
    public void load(long amountMinor) {
        this.balanceMinor += amountMinor;
    }

    /** Deducts a spend from an ACTIVE card; the amount must not exceed the available balance. */
    public void spend(long amountMinor) {
        if (status != CardStatus.ACTIVE) {
            throw new IllegalStateException("card is " + status);
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amountMinor > balanceMinor) {
            throw new IllegalArgumentException(
                    "amount " + amountMinor + " exceeds card balance " + balanceMinor);
        }
        this.balanceMinor -= amountMinor;
    }

    /** Returns a previously spent amount to the card balance. */
    public void refund(long amountMinor) {
        this.balanceMinor += amountMinor;
    }

    public void freeze() {
        this.status = CardStatus.FROZEN;
    }

    public void unfreeze() {
        this.status = CardStatus.ACTIVE;
    }

    public void close() {
        this.status = CardStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getHolderRef() { return holderRef; }
    public CardType getType() { return type; }
    public String getMaskedPan() { return maskedPan; }
    public String getCurrency() { return currency; }
    public long getBalanceMinor() { return balanceMinor; }
    public CardStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
