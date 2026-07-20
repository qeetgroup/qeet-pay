/**
 * ONDC payment layer (PRD Module 13.4, Phase 3) — multi-party settlement for orders placed over the
 * Open Network for Digital Commerce. A buyer-app order is collected by the platform merchant on behalf
 * of one or more network parties (a seller, plus arbitrary additional legs such as a logistics
 * partner — the split is not fixed at three parties, per PRD Module 13.4 edge case). The collected
 * gross is held escrow-like on order creation and, <b>only after fulfilment</b>, settled per party:
 * the operator keeps a commission (+ GST on it) and deducts statutory TCS (CGST Act §52), leaving each
 * party's net payable.
 *
 * <p>Ledger flow (all balanced, append-only):
 * <ul>
 *   <li><b>create</b> — hold: debit {@code settlement} / credit on-demand {@code ondc_hold} (Σgross);</li>
 *   <li><b>settle</b> (post-fulfilment) — release: debit {@code ondc_hold} Σgross / credit
 *       {@code revenue} (Σcommission) + {@code tax_payable} (Σcommission GST + Σ TCS) +
 *       {@code liability} (Σparty net);</li>
 *   <li><b>cancel</b> — the exact offsetting entry of whatever has been posted (never an UPDATE).</li>
 * </ul>
 *
 * <p>Per-party TCS/GST is retained for GSTR-8 / settlement audit. Merchant-scoped via platform RLS.
 * Depends only on {@code platform} (tenancy + outbox) and {@code ledger}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "ONDC",
        allowedDependencies = {"platform", "ledger"})
package com.qeetgroup.qeetpay.ondc;
