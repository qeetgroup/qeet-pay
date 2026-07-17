package com.qeetgroup.qeetpay.bnpl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A BNPL agreement: an order funded upfront to the merchant, repaid by the customer in installments. */
@Entity
@Table(name = "bnpl_agreements", schema = "bnpl")
public class BnplAgreement {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "order_ref", nullable = false)
    private String orderRef;

    @Column(name = "order_amount_minor", nullable = false)
    private long orderAmountMinor;

    @Column(name = "interest_bps", nullable = false)
    private int interestBps;

    @Column(name = "total_payable_minor", nullable = false)
    private long totalPayableMinor;

    @Column(name = "installments_count", nullable = false)
    private int installmentsCount;

    @Column(name = "paid_installments", nullable = false)
    private int paidInstallments = 0;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BnplStatus status = BnplStatus.ACTIVE;

    @Column(name = "sale_entry_id", nullable = false)
    private UUID saleEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "settled_at")
    private Instant settledAt;

    protected BnplAgreement() {}

    public BnplAgreement(
            UUID merchantId, String customerRef, String orderRef, long orderAmountMinor, int interestBps,
            long totalPayableMinor, int installmentsCount, String currency, UUID saleEntryId) {
        this.merchantId = merchantId;
        this.customerRef = customerRef;
        this.orderRef = orderRef;
        this.orderAmountMinor = orderAmountMinor;
        this.interestBps = interestBps;
        this.totalPayableMinor = totalPayableMinor;
        this.installmentsCount = installmentsCount;
        this.currency = currency;
        this.saleEntryId = saleEntryId;
    }

    /** Records one repaid installment; settles the agreement once every installment is paid. */
    public void markInstallmentPaid() {
        if (status != BnplStatus.ACTIVE) {
            throw new IllegalStateException("agreement is " + status + ", not accepting repayments");
        }
        this.paidInstallments += 1;
        if (paidInstallments >= installmentsCount) {
            this.status = BnplStatus.SETTLED;
            this.settledAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCustomerRef() { return customerRef; }
    public String getOrderRef() { return orderRef; }
    public long getOrderAmountMinor() { return orderAmountMinor; }
    public int getInterestBps() { return interestBps; }
    public long getTotalPayableMinor() { return totalPayableMinor; }
    public int getInstallmentsCount() { return installmentsCount; }
    public int getPaidInstallments() { return paidInstallments; }
    public String getCurrency() { return currency; }
    public BnplStatus getStatus() { return status; }
    public UUID getSaleEntryId() { return saleEntryId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
}
