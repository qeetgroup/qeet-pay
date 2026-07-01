package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Additional GSTIN registration for a merchant (supports multi-state businesses). */
@Entity
@Table(name = "merchant_gstins", schema = "platform")
public class MerchantGstin {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String gstin;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "state_code", nullable = false)
    private String stateCode;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MerchantGstin() {}

    public MerchantGstin(UUID merchantId, String gstin, String legalName, String stateCode, boolean isDefault) {
        this.merchantId = merchantId;
        this.gstin = gstin;
        this.legalName = legalName;
        this.stateCode = stateCode;
        this.isDefault = isDefault;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getGstin() { return gstin; }
    public String getLegalName() { return legalName; }
    public String getStateCode() { return stateCode; }
    public boolean isDefault() { return isDefault; }
}
