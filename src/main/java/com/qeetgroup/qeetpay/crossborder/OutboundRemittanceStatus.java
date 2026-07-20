package com.qeetgroup.qeetpay.crossborder;

/**
 * Outbound (import) remittance lifecycle (PRD Module 14.4). CREATED once the INR (+ TCS) has been
 * debited and the SWIFT instruction issued; REMITTED when the wire settles at the beneficiary;
 * FAILED if the wire is rejected — which posts an offsetting ledger entry returning the funds.
 */
public enum OutboundRemittanceStatus {
    CREATED,
    REMITTED,
    FAILED
}
