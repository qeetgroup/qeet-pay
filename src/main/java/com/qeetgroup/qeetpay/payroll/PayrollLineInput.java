package com.qeetgroup.qeetpay.payroll;

import com.qeetgroup.qeetpay.payouts.PayoutRail;

/**
 * One requested employee disbursement in a payroll run. Gross and the statutory components
 * (PF/ESI/PT/TDS) are integer minor units (paise); net pay is derived (gross − statutory).
 * {@code accountNumber}/{@code ifsc} are optional — when both are present the destination is
 * penny-drop verified via the kyb module before the run is staged.
 */
public record PayrollLineInput(
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
        long tdsMinor) {}
