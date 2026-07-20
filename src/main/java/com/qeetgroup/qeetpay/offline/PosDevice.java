package com.qeetgroup.qeetpay.offline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A registered POS / Tap-to-Pay device (Android POS or soft-POS phone). Captures in-person payments. */
@Entity
@Table(name = "pos_devices", schema = "offline")
public class PosDevice {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String label;

    @Column(name = "serial_no", nullable = false)
    private String serialNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosDeviceStatus status = PosDeviceStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PosDevice() {}

    public PosDevice(UUID merchantId, String label, String serialNo) {
        this.merchantId = merchantId;
        this.label = label;
        this.serialNo = serialNo;
    }

    public boolean isActive() {
        return status == PosDeviceStatus.ACTIVE;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getLabel() { return label; }
    public String getSerialNo() { return serialNo; }
    public PosDeviceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
