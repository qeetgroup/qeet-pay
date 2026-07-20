/**
 * Offline &amp; Rural payments module (PRD Module 15). Four India-first "reach" rails, all simulated
 * (sandbox), consistent with how the rest of Qeet Pay stands in for external providers:
 *
 * <ul>
 *   <li><b>Bharat QR</b> — a single unified QR whose payload accepts UPI + RuPay + Visa + Mastercard
 *       (dynamic when an amount is supplied, static/open otherwise). Generation is not a payment, so
 *       it posts nothing to the ledger.
 *   <li><b>UPI Lite</b> — an on-device low-value wallet ({@code balance_minor}). A top-up posts
 *       money-in (debit {@code settlement} / credit {@code liability}); a spend posts (debit
 *       {@code liability} / credit {@code revenue}). Per-transaction (₹500) and per-day (₹2,000)
 *       limits are enforced before any posting.
 *   <li><b>UPI 123Pay</b> — a feature-phone/IVR payment intent (create → confirm). Confirming posts
 *       the canonical money-in entry (debit {@code settlement} / credit {@code revenue}).
 *   <li><b>POS / Tap-to-Pay</b> — an in-person capture on a registered device, posting money-in
 *       (debit {@code settlement} / credit {@code revenue}).
 * </ul>
 *
 * <p>All money movement flows through the {@code ledger} module; writes are outbox-published and
 * merchant-scoped via platform RLS. Amounts are integer minor units (paise).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Offline",
        allowedDependencies = {"platform", "ledger"})
package com.qeetgroup.qeetpay.offline;
