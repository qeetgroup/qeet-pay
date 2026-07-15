package com.qeetgroup.qeetpay.dunning;

/**
 * Classification of a payment/mandate failure (PRD Module 04.1). UPI/NACH failures cluster into a
 * handful of categories that call for very different recovery tactics — the AI dunning layer maps a
 * raw provider failure code to one of these, which then drives retry timing and channel choice.
 */
public enum FailureCategory {
    /** Payer balance too low — retry aligned with likely credit (salary) cycles. */
    INSUFFICIENT_FUNDS,
    /** Per-transaction / daily UPI limit hit — retry after the limit window resets. */
    LIMIT_EXCEEDED,
    /** Transient bank/gateway/timeout error — a prompt silent retry usually clears it. */
    TECHNICAL_DECLINE,
    /** Issuer/risk block — do not auto-retry; route to manual review. */
    RISK_DECLINE,
    /** AutoPay mandate missing/revoked/expired — customer must re-authorise before retrying. */
    MANDATE_ISSUE,
    /** Needs the customer to act (re-authenticate, update method) — nudge, then retry. */
    CUSTOMER_ACTION,
    /** Unrecognised code — fall back to the default retry policy. */
    UNKNOWN
}
