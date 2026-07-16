package com.qeetgroup.qeetpay.insurance;

/**
 * The embedded-insurance products Qeet Pay can attach to a payment (PRD Module 10): protection against
 * a failed/disputed payment, cover for a fraudulent charge, or cover for an interrupted subscription.
 */
public enum InsuranceProduct {
    PAYMENT_PROTECTION,
    FRAUD_COVER,
    SUBSCRIPTION_INTERRUPTION
}
