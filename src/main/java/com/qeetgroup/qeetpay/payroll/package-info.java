/**
 * Payroll module — Qeet People payroll disbursement (PRD Module 02.5 &amp; Module 18.4). A payroll run
 * from Qeet People is staged as a batch of employee lines (gross + statutory PF/ESI/PT/TDS); net pay =
 * gross − statutory. Destination accounts are penny-drop verified via the {@code kyb} module. On
 * maker-checker approval each line's net pay is disbursed through the existing {@code payouts} public
 * API (a bulk payout batch → debit liability / credit bank in {@code ledger}); the payout + ledger
 * reference and status are captured back onto each line for the combined salary-slip + receipt.
 * Merchant-scoped via platform RLS. This module never reimplements the payout rail.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Payroll",
        allowedDependencies = {"platform", "payouts", "kyb"})
package com.qeetgroup.qeetpay.payroll;
