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
 * An Ultimate Beneficial Owner (UBO) of a merchant (PRD Module 19). Per the RBI Master Directions on
 * KYC, a beneficial owner is any natural person holding {@code > 10%} equity — expressed here in basis
 * points ({@code ownershipBps > 1000}). PAN is verified via the shared {@link KybVerificationAdapter}.
 */
@Entity
@Table(name = "beneficial_owners", schema = "kyb")
public class BeneficialOwner {

    /** RBI beneficial-owner equity threshold, in basis points (10% = 1000 bps). */
    public static final int MIN_OWNERSHIP_BPS = 1000;

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String name;

    @Column private String pan;

    @Column private String din;

    @Column(nullable = false)
    private String nationality = "IN";

    @Column(name = "ownership_bps", nullable = false)
    private int ownershipBps;

    @Column(name = "is_control_person", nullable = false)
    private boolean controlPerson = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "pan_status", nullable = false)
    private KycStatus panStatus = KycStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BeneficialOwner() {}

    public BeneficialOwner(
            UUID merchantId, String name, String pan, String din, String nationality,
            int ownershipBps, boolean controlPerson) {
        this.merchantId = merchantId;
        this.name = name;
        this.pan = pan;
        this.din = din;
        if (nationality != null && !nationality.isBlank()) {
            this.nationality = nationality;
        }
        this.ownershipBps = ownershipBps;
        this.controlPerson = controlPerson;
    }

    public void setPanStatus(KycStatus status) {
        this.panStatus = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getName() { return name; }
    public String getPan() { return pan; }
    public String getDin() { return din; }
    public String getNationality() { return nationality; }
    public int getOwnershipBps() { return ownershipBps; }
    public boolean isControlPerson() { return controlPerson; }
    public KycStatus getPanStatus() { return panStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
