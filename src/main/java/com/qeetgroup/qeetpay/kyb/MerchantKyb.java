package com.qeetgroup.qeetpay.kyb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * KYB (Know Your Business) verification record for a merchant.
 * Each step (PAN / GSTIN / bank) transitions independently: PENDING → VERIFIED | REJECTED.
 * {@code overallStatus} = VERIFIED only when all three steps are VERIFIED.
 */
@Entity
@Table(name = "merchant_kyb", schema = "platform")
public class MerchantKyb {

    public static final String PENDING  = "PENDING";
    public static final String VERIFIED = "VERIFIED";
    public static final String REJECTED = "REJECTED";

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false, unique = true)
    private UUID merchantId;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "pan_status", nullable = false)
    private String panStatus = PENDING;

    @Column
    private String gstin;

    @Column(name = "gstin_status", nullable = false)
    private String gstinStatus = PENDING;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "bank_ifsc")
    private String bankIfsc;

    @Column(name = "bank_status", nullable = false)
    private String bankStatus = PENDING;

    @Column(name = "overall_status", nullable = false)
    private String overallStatus = PENDING;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected MerchantKyb() {}

    public MerchantKyb(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public void submitPan(String pan, String result) {
        this.panNumber = pan;
        this.panStatus = result;
        recalculateOverall();
    }

    public void submitGstin(String gstin, String result) {
        this.gstin = gstin;
        this.gstinStatus = result;
        recalculateOverall();
    }

    public void submitBank(String account, String ifsc, String result) {
        this.bankAccount = account;
        this.bankIfsc = ifsc;
        this.bankStatus = result;
        recalculateOverall();
    }

    private void recalculateOverall() {
        if (REJECTED.equals(panStatus) || REJECTED.equals(gstinStatus) || REJECTED.equals(bankStatus)) {
            this.overallStatus = REJECTED;
        } else if (VERIFIED.equals(panStatus) && VERIFIED.equals(gstinStatus) && VERIFIED.equals(bankStatus)) {
            this.overallStatus = VERIFIED;
            this.verifiedAt = Instant.now();
        } else {
            this.overallStatus = PENDING;
        }
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getPanNumber() { return panNumber; }
    public String getPanStatus() { return panStatus; }
    public String getGstin() { return gstin; }
    public String getGstinStatus() { return gstinStatus; }
    public String getBankAccount() { return bankAccount; }
    public String getBankIfsc() { return bankIfsc; }
    public String getBankStatus() { return bankStatus; }
    public String getOverallStatus() { return overallStatus; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
}
