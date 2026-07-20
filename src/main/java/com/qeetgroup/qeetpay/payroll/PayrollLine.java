package com.qeetgroup.qeetpay.payroll;

import com.qeetgroup.qeetpay.payouts.PayoutRail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee's line in a payroll run: gross, the statutory deductions (PF/ESI/PT/TDS), and the
 * derived net pay that is actually disbursed. All amounts are integer minor units (paise). On
 * disbursal the underlying payout id, ledger entry id, and status are captured back onto the line so
 * a combined salary-slip + receipt (with the payout/ledger reference) can be generated.
 */
@Entity
@Table(name = "payroll_lines", schema = "payroll")
public class PayrollLine {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "employee_ref", nullable = false)
    private String employeeRef;

    @Column(name = "employee_name")
    private String employeeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutRail rail;

    @Column(nullable = false)
    private String destination;

    @Column(name = "account_number")
    private String accountNumber;

    @Column private String ifsc;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verification_result")
    private String verificationResult;

    @Column(name = "gross_minor", nullable = false)
    private long grossMinor;

    @Column(name = "pf_minor", nullable = false)
    private long pfMinor;

    @Column(name = "esi_minor", nullable = false)
    private long esiMinor;

    @Column(name = "pt_minor", nullable = false)
    private long ptMinor;

    @Column(name = "tds_minor", nullable = false)
    private long tdsMinor;

    @Column(name = "net_pay_minor", nullable = false)
    private long netPayMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollLineStatus status = PayrollLineStatus.PENDING;

    @Column(name = "payout_id")
    private UUID payoutId;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PayrollLine() {}

    public PayrollLine(
            UUID batchId,
            UUID merchantId,
            String employeeRef,
            String employeeName,
            PayoutRail rail,
            String destination,
            String accountNumber,
            String ifsc,
            long grossMinor,
            long pfMinor,
            long esiMinor,
            long ptMinor,
            long tdsMinor,
            long netPayMinor) {
        this.batchId = batchId;
        this.merchantId = merchantId;
        this.employeeRef = employeeRef;
        this.employeeName = employeeName;
        this.rail = rail;
        this.destination = destination;
        this.accountNumber = accountNumber;
        this.ifsc = ifsc;
        this.grossMinor = grossMinor;
        this.pfMinor = pfMinor;
        this.esiMinor = esiMinor;
        this.ptMinor = ptMinor;
        this.tdsMinor = tdsMinor;
        this.netPayMinor = netPayMinor;
    }

    /** Records a penny-drop verification outcome for this line's destination account. */
    public void recordVerification(boolean verified, String result) {
        this.verified = verified;
        this.verificationResult = result;
        touch();
    }

    public void markPaid(UUID payoutId, UUID ledgerEntryId) {
        this.payoutId = payoutId;
        this.ledgerEntryId = ledgerEntryId;
        this.status = PayrollLineStatus.PAID;
        this.failureReason = null;
        touch();
    }

    public void markFailed(UUID payoutId, String reason) {
        this.payoutId = payoutId;
        this.status = PayrollLineStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    public long statutoryMinor() {
        return pfMinor + esiMinor + ptMinor + tdsMinor;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getEmployeeRef() {
        return employeeRef;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public PayoutRail getRail() {
        return rail;
    }

    public String getDestination() {
        return destination;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getIfsc() {
        return ifsc;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getVerificationResult() {
        return verificationResult;
    }

    public long getGrossMinor() {
        return grossMinor;
    }

    public long getPfMinor() {
        return pfMinor;
    }

    public long getEsiMinor() {
        return esiMinor;
    }

    public long getPtMinor() {
        return ptMinor;
    }

    public long getTdsMinor() {
        return tdsMinor;
    }

    public long getNetPayMinor() {
        return netPayMinor;
    }

    public PayrollLineStatus getStatus() {
        return status;
    }

    public UUID getPayoutId() {
        return payoutId;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
