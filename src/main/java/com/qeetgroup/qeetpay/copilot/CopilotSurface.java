package com.qeetgroup.qeetpay.copilot;

/**
 * The three copilot surfaces (PRD Module 12.5 / N7 / 17). A conversation is bound to exactly one, so a
 * thread never mixes treasury and reconciliation questions.
 */
public enum CopilotSurface {
    /** Treasury &amp; cash-flow copilot (PRD Module 12.5) — cash-flow / settlement / working-capital. */
    TREASURY,
    /** Reconciliation copilot (PRD N7) — explains recon breaks / leakage. */
    RECONCILIATION,
    /** Natural-language query (PRD Module 17) — plain English over the merchant's own metrics. */
    QUERY
}
