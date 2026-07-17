package com.qeetgroup.qeetpay.bnpl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One scheduled installment of a BNPL agreement, repaid by the customer to the platform. */
@Entity
@Table(name = "bnpl_installments", schema = "bnpl")
public class BnplInstallment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "agreement_id", nullable = false)
    private UUID agreementId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status = InstallmentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected BnplInstallment() {}

    public BnplInstallment(UUID agreementId, UUID merchantId, int seq, LocalDate dueDate, long amountMinor) {
        this.agreementId = agreementId;
        this.merchantId = merchantId;
        this.seq = seq;
        this.dueDate = dueDate;
        this.amountMinor = amountMinor;
    }

    /** Marks this installment repaid; a no-op guard belongs to the caller. */
    public void markPaid() {
        if (status == InstallmentStatus.PAID) {
            throw new IllegalStateException("installment " + seq + " is already paid");
        }
        this.status = InstallmentStatus.PAID;
        this.paidAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgreementId() { return agreementId; }
    public UUID getMerchantId() { return merchantId; }
    public int getSeq() { return seq; }
    public LocalDate getDueDate() { return dueDate; }
    public long getAmountMinor() { return amountMinor; }
    public InstallmentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
}
