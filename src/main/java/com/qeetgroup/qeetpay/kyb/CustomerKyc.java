package com.qeetgroup.qeetpay.kyb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * End-customer KYC record (PRD Module 19): Aadhaar-OTP e-KYC + PAN + the consent flags mandated for
 * Aadhaar authentication. Each check (aadhaar / pan) transitions independently
 * {@code PENDING → VERIFIED | REJECTED}; {@code overallStatus} is VERIFIED only when consent is
 * captured and both checks are VERIFIED.
 *
 * <p><b>Privacy:</b> the full Aadhaar number is never persisted — only its last four digits
 * ({@code aadhaarLast4}) and an OTP transaction reference are retained.
 */
@Entity
@Table(name = "customer_kyc", schema = "kyb")
public class CustomerKyc {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "aadhaar_last4")
    private String aadhaarLast4;

    @Column(name = "aadhaar_txn_id")
    private String aadhaarTxnId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aadhaar_status", nullable = false)
    private KycStatus aadhaarStatus = KycStatus.PENDING;

    @Column private String pan;

    @Enumerated(EnumType.STRING)
    @Column(name = "pan_status", nullable = false)
    private KycStatus panStatus = KycStatus.PENDING;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_artifact")
    private String consentArtifact;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", nullable = false)
    private KycStatus overallStatus = KycStatus.PENDING;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CustomerKyc() {}

    public CustomerKyc(UUID merchantId, String customerRef, String fullName) {
        this.merchantId = merchantId;
        this.customerRef = customerRef;
        this.fullName = fullName;
    }

    /** Records the Aadhaar-authentication consent artifact (required before e-KYC). */
    public void giveConsent(String artifact) {
        this.consentGiven = true;
        this.consentAt = Instant.now();
        this.consentArtifact = artifact;
        touch();
    }

    /** Marks that an Aadhaar-OTP challenge has been issued (stores masked last-4 + txn ref). */
    public void initiateAadhaar(String aadhaarLast4, String txnId) {
        this.aadhaarLast4 = aadhaarLast4;
        this.aadhaarTxnId = txnId;
        this.aadhaarStatus = KycStatus.PENDING;
        touch();
    }

    public void setAadhaarStatus(KycStatus status) {
        this.aadhaarStatus = status;
        recalculateOverall();
    }

    public void submitPan(String pan, KycStatus status) {
        this.pan = pan;
        this.panStatus = status;
        recalculateOverall();
    }

    private void recalculateOverall() {
        if (aadhaarStatus == KycStatus.REJECTED || panStatus == KycStatus.REJECTED) {
            this.overallStatus = KycStatus.REJECTED;
        } else if (consentGiven && aadhaarStatus == KycStatus.VERIFIED && panStatus == KycStatus.VERIFIED) {
            this.overallStatus = KycStatus.VERIFIED;
            this.verifiedAt = Instant.now();
        } else {
            this.overallStatus = KycStatus.PENDING;
        }
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCustomerRef() { return customerRef; }
    public String getFullName() { return fullName; }
    public String getAadhaarLast4() { return aadhaarLast4; }
    public String getAadhaarTxnId() { return aadhaarTxnId; }
    public KycStatus getAadhaarStatus() { return aadhaarStatus; }
    public String getPan() { return pan; }
    public KycStatus getPanStatus() { return panStatus; }
    public boolean isConsentGiven() { return consentGiven; }
    public Instant getConsentAt() { return consentAt; }
    public String getConsentArtifact() { return consentArtifact; }
    public KycStatus getOverallStatus() { return overallStatus; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
