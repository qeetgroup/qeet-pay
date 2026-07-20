package com.qeetgroup.qeetpay.crossborder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An outbound / import remittance (PRD Module 14.4): paying a foreign vendor/SaaS/cloud in a foreign
 * currency via SWIFT. Records the beneficiary + FEMA purpose code, the foreign amount, the captured FX
 * rate, the INR principal wired, the LRS financial-year running total, the 2.5% TCS collected above the
 * LRS threshold, and the total INR debited from the merchant's settlement balance. Merchant-scoped via
 * platform RLS; status mutates CREATED → REMITTED / FAILED.
 */
@Entity
@Table(name = "outbound_remittances", schema = "crossborder")
public class OutboundRemittance {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_swift", nullable = false)
    private String beneficiarySwift;

    @Column(name = "beneficiary_account", nullable = false)
    private String beneficiaryAccount;

    @Column(name = "beneficiary_country", nullable = false)
    private String beneficiaryCountry;

    @Column(name = "purpose_code", nullable = false)
    private String purposeCode;

    @Column(nullable = false)
    private String currency;

    @Column(name = "foreign_amount_minor", nullable = false)
    private long foreignAmountMinor;

    @Column(name = "fx_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "principal_inr_minor", nullable = false)
    private long principalInrMinor;

    @Column(name = "tcs_minor", nullable = false)
    private long tcsMinor;

    @Column(name = "inr_debited_minor", nullable = false)
    private long inrDebitedMinor;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "lrs_cumulative_before_minor", nullable = false)
    private long lrsCumulativeBeforeMinor;

    @Column(name = "lrs_cumulative_after_minor", nullable = false)
    private long lrsCumulativeAfterMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboundRemittanceStatus status = OutboundRemittanceStatus.CREATED;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "reversal_entry_id")
    private UUID reversalEntryId;

    @Column(name = "remittance_reference")
    private String remittanceReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "remitted_at")
    private Instant remittedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected OutboundRemittance() {}

    public OutboundRemittance(
            UUID merchantId, String beneficiaryName, String beneficiarySwift, String beneficiaryAccount,
            String beneficiaryCountry, String purposeCode, String currency, long foreignAmountMinor,
            BigDecimal fxRate, long principalInrMinor, long tcsMinor, long inrDebitedMinor,
            String financialYear, long lrsCumulativeBeforeMinor, long lrsCumulativeAfterMinor,
            UUID ledgerEntryId) {
        this.merchantId = merchantId;
        this.beneficiaryName = beneficiaryName;
        this.beneficiarySwift = beneficiarySwift;
        this.beneficiaryAccount = beneficiaryAccount;
        this.beneficiaryCountry = beneficiaryCountry;
        this.purposeCode = purposeCode;
        this.currency = currency;
        this.foreignAmountMinor = foreignAmountMinor;
        this.fxRate = fxRate;
        this.principalInrMinor = principalInrMinor;
        this.tcsMinor = tcsMinor;
        this.inrDebitedMinor = inrDebitedMinor;
        this.financialYear = financialYear;
        this.lrsCumulativeBeforeMinor = lrsCumulativeBeforeMinor;
        this.lrsCumulativeAfterMinor = lrsCumulativeAfterMinor;
        this.ledgerEntryId = ledgerEntryId;
    }

    public void markRemitted(String remittanceReference) {
        this.status = OutboundRemittanceStatus.REMITTED;
        this.remittanceReference = remittanceReference;
        this.remittedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markFailed(String failureReason, UUID reversalEntryId) {
        this.status = OutboundRemittanceStatus.FAILED;
        this.failureReason = failureReason;
        this.reversalEntryId = reversalEntryId;
        this.failedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getBeneficiaryName() { return beneficiaryName; }
    public String getBeneficiarySwift() { return beneficiarySwift; }
    public String getBeneficiaryAccount() { return beneficiaryAccount; }
    public String getBeneficiaryCountry() { return beneficiaryCountry; }
    public String getPurposeCode() { return purposeCode; }
    public String getCurrency() { return currency; }
    public long getForeignAmountMinor() { return foreignAmountMinor; }
    public BigDecimal getFxRate() { return fxRate; }
    public long getPrincipalInrMinor() { return principalInrMinor; }
    public long getTcsMinor() { return tcsMinor; }
    public long getInrDebitedMinor() { return inrDebitedMinor; }
    public String getFinancialYear() { return financialYear; }
    public long getLrsCumulativeBeforeMinor() { return lrsCumulativeBeforeMinor; }
    public long getLrsCumulativeAfterMinor() { return lrsCumulativeAfterMinor; }
    public OutboundRemittanceStatus getStatus() { return status; }
    public UUID getLedgerEntryId() { return ledgerEntryId; }
    public UUID getReversalEntryId() { return reversalEntryId; }
    public String getRemittanceReference() { return remittanceReference; }
    public String getFailureReason() { return failureReason; }
    public Instant getRemittedAt() { return remittedAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
