package com.qeetgroup.qeetpay.payroll;

/**
 * A combined salary-slip + payment receipt for one employee line (PRD Module 02.5: "combined salary
 * slip + payment receipt with UTR"). Computed on demand from the persisted {@link PayrollLine} — the
 * earnings/deductions breakdown plus the disbursal reference (payout id + ledger entry id) and status.
 *
 * <p>Note: the disbursal reference is the Qeet Pay {@code payoutId} + {@code ledgerEntryId}; the
 * provider-side UTR ({@code providerPayoutId}) is not exposed by the payouts public API, so these are
 * recorded as the traceable receipt reference instead.
 */
public record SalarySlip(
        String lineId,
        String batchId,
        String employeeRef,
        String employeeName,
        String period,
        String currency,
        long grossMinor,
        long pfMinor,
        long esiMinor,
        long ptMinor,
        long tdsMinor,
        long statutoryMinor,
        long netPayMinor,
        String status,
        String payoutRef,
        String ledgerEntryId) {

    static SalarySlip of(PayrollBatch batch, PayrollLine line) {
        return new SalarySlip(
                line.getId().toString(),
                batch.getId().toString(),
                line.getEmployeeRef(),
                line.getEmployeeName(),
                batch.getPeriod(),
                batch.getCurrency(),
                line.getGrossMinor(),
                line.getPfMinor(),
                line.getEsiMinor(),
                line.getPtMinor(),
                line.getTdsMinor(),
                line.statutoryMinor(),
                line.getNetPayMinor(),
                line.getStatus().name(),
                line.getPayoutId() == null ? null : line.getPayoutId().toString(),
                line.getLedgerEntryId() == null ? null : line.getLedgerEntryId().toString());
    }
}
