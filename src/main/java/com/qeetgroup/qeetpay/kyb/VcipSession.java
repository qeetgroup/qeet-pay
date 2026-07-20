package com.qeetgroup.qeetpay.kyb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A V-CIP (Video-based Customer Identification Process) session for a merchant's signatory/director
 * (PRD Module 19, RBI Master Directions on KYC). The state machine is enforced here:
 * {@code SCHEDULED → IN_PROGRESS → COMPLETED}, with {@code FAILED} reachable from either
 * non-terminal state.
 *
 * <p><b>Minimal biometric retention.</b> Only a {@code biometricRef} (an opaque token/hash — never
 * raw biometric data) and a {@code livenessScore} are retained, with a bounded
 * {@code retentionExpiresAt} window. The reference is purged (nulled) on failure and can be purged
 * once the retention window lapses.
 */
@Entity
@Table(name = "vcip_sessions", schema = "kyb")
public class VcipSession {

    /** Days the biometric reference is retained after a completed session before it may be purged. */
    public static final int BIOMETRIC_RETENTION_DAYS = 180;

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "subject_name", nullable = false)
    private String subjectName;

    @Column(name = "subject_ref")
    private String subjectRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VcipStatus status = VcipStatus.SCHEDULED;

    @Column(name = "agent_id")
    private String agentId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "biometric_ref")
    private String biometricRef;

    @Column(name = "liveness_score")
    private Integer livenessScore;

    @Column(name = "geo_tag")
    private String geoTag;

    @Column(name = "retention_expires_at")
    private Instant retentionExpiresAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected VcipSession() {}

    public VcipSession(UUID merchantId, String subjectName, String subjectRef, String agentId, Instant scheduledAt) {
        this.merchantId = merchantId;
        this.subjectName = subjectName;
        this.subjectRef = subjectRef;
        this.agentId = agentId;
        this.scheduledAt = scheduledAt != null ? scheduledAt : Instant.now();
    }

    /** SCHEDULED → IN_PROGRESS. */
    public void start() {
        requireStatus(VcipStatus.SCHEDULED, "start");
        this.status = VcipStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        touch();
    }

    /** IN_PROGRESS → COMPLETED, retaining only a biometric reference + liveness score. */
    public void complete(String biometricRef, Integer livenessScore, String geoTag) {
        requireStatus(VcipStatus.IN_PROGRESS, "complete");
        this.status = VcipStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.biometricRef = biometricRef;
        this.livenessScore = livenessScore;
        this.geoTag = geoTag;
        this.retentionExpiresAt = this.completedAt.plus(BIOMETRIC_RETENTION_DAYS, ChronoUnit.DAYS);
        touch();
    }

    /** {SCHEDULED | IN_PROGRESS} → FAILED, purging any biometric reference. */
    public void fail(String reason) {
        if (status == VcipStatus.COMPLETED || status == VcipStatus.FAILED) {
            throw new IllegalStateException("cannot fail a " + status + " V-CIP session");
        }
        this.status = VcipStatus.FAILED;
        this.failureReason = reason;
        purgeBiometrics(); // minimal retention: never keep biometrics for a failed session
        touch();
    }

    /** Drops the retained biometric reference (called on failure, or once retention lapses). */
    public void purgeBiometrics() {
        this.biometricRef = null;
        this.livenessScore = null;
        this.retentionExpiresAt = null;
        touch();
    }

    private void requireStatus(VcipStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("cannot " + action + " a V-CIP session in status " + status);
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getSubjectName() { return subjectName; }
    public String getSubjectRef() { return subjectRef; }
    public VcipStatus getStatus() { return status; }
    public String getAgentId() { return agentId; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getBiometricRef() { return biometricRef; }
    public Integer getLivenessScore() { return livenessScore; }
    public String getGeoTag() { return geoTag; }
    public Instant getRetentionExpiresAt() { return retentionExpiresAt; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
