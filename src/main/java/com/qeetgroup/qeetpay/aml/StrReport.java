package com.qeetgroup.qeetpay.aml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A Suspicious Transaction Report (PMLA, filed with FIU-IND). {@code payload} is the generated
 * FIU-IND-style report as a JSON string. Created DRAFT; once "filed" it holds the regulator's
 * {@code fiuReferenceId} and flips to FILED.
 */
@Entity
@Table(name = "str_reports", schema = "aml")
public class StrReport {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "case_id")
    private UUID caseId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String grounds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrStatus status = StrStatus.DRAFT;

    /** The FIU-IND-style report body as JSON. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "fiu_reference_id")
    private String fiuReferenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "filed_at")
    private Instant filedAt;

    protected StrReport() {}

    public StrReport(UUID merchantId, UUID caseId, String subject, String grounds, String payload) {
        this.merchantId = merchantId;
        this.caseId = caseId;
        this.subject = subject;
        this.grounds = grounds;
        this.payload = payload;
    }

    public void markFiled(String fiuReferenceId) {
        if (status == StrStatus.FILED) {
            throw new IllegalStateException("STR already filed");
        }
        this.status = StrStatus.FILED;
        this.fiuReferenceId = fiuReferenceId;
        this.filedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getCaseId() { return caseId; }
    public String getSubject() { return subject; }
    public String getGrounds() { return grounds; }
    public StrStatus getStatus() { return status; }
    public String getPayload() { return payload; }
    public String getFiuReferenceId() { return fiuReferenceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFiledAt() { return filedAt; }
}
