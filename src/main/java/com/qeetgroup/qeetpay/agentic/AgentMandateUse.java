package com.qeetgroup.qeetpay.agentic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit of one authorization decision (ALLOW or DENY) made against a mandate. */
@Entity
@Table(name = "agent_mandate_uses", schema = "agentic")
public class AgentMandateUse {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "mandate_id", nullable = false)
    private UUID mandateId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "idem_key")
    private String idemKey;

    @Column(nullable = false)
    private String operation;

    @Column(name = "payee_ref")
    private String payeeRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AgentMandateUse() {}

    public AgentMandateUse(
            UUID mandateId,
            UUID merchantId,
            String idemKey,
            String operation,
            String payeeRef,
            long amountMinor,
            boolean allowed,
            String reason) {
        this.mandateId = mandateId;
        this.merchantId = merchantId;
        this.idemKey = idemKey;
        this.operation = operation;
        this.payeeRef = payeeRef;
        this.amountMinor = amountMinor;
        this.allowed = allowed;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getMandateId() { return mandateId; }
    public String getIdemKey() { return idemKey; }
    public String getOperation() { return operation; }
    public String getPayeeRef() { return payeeRef; }
    public long getAmountMinor() { return amountMinor; }
    public boolean isAllowed() { return allowed; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
