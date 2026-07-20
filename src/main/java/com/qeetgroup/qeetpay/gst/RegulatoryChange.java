package com.qeetgroup.qeetpay.gst;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An announced-but-not-yet-effective GST change for one HSN/SAC (PRD Module 06.5 "Regulatory-Change
 * Impact Radar"). Merchant-scoped so each merchant tracks the changes relevant to its supply mix; the
 * radar forecasts the impact of the change over the merchant's existing GST invoices.
 */
@Entity
@Table(name = "regulatory_changes", schema = "gst")
public class RegulatoryChange {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "hsn_sac", nullable = false)
    private String hsnSac;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private RegChangeType changeType = RegChangeType.RATE_CHANGE;

    /** The current rate, if known — nullable; the radar otherwise reads the rate off each invoice line. */
    @Column(name = "old_rate_pct")
    private Integer oldRatePct;

    @Column(name = "new_rate_pct", nullable = false)
    private int newRatePct;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false)
    private String title;

    @Column(name = "source")
    private String source;

    @Column(name = "announced_at", nullable = false)
    private Instant announcedAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RegulatoryChange() {}

    public RegulatoryChange(
            UUID merchantId,
            String hsnSac,
            RegChangeType changeType,
            Integer oldRatePct,
            int newRatePct,
            LocalDate effectiveDate,
            String title,
            String source) {
        this.merchantId = merchantId;
        this.hsnSac = hsnSac;
        this.changeType = changeType == null ? RegChangeType.RATE_CHANGE : changeType;
        this.oldRatePct = oldRatePct;
        this.newRatePct = newRatePct;
        this.effectiveDate = effectiveDate;
        this.title = title;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getHsnSac() {
        return hsnSac;
    }

    public RegChangeType getChangeType() {
        return changeType;
    }

    public Integer getOldRatePct() {
        return oldRatePct;
    }

    public int getNewRatePct() {
        return newRatePct;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getTitle() {
        return title;
    }

    public String getSource() {
        return source;
    }

    public Instant getAnnouncedAt() {
        return announcedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
