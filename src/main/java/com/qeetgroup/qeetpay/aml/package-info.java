/**
 * AML / CFT module (PRD §7.7, Module 20-adjacent, Phase 2) — Anti-Money-Laundering and Combating the
 * Financing of Terrorism. Four capabilities, all merchant-scoped via platform RLS:
 *
 * <ul>
 *   <li><b>Sanctions + PEP screening</b> — a party (individual / business / payout beneficiary) is
 *       screened against OFAC / UN / PEP lists through a pluggable {@link
 *       com.qeetgroup.qeetpay.aml.SanctionsListAdapter} (sandbox in-memory list by default, mirroring
 *       {@code kyb}). A hit is persisted as a {@link com.qeetgroup.qeetpay.aml.SanctionScreening} and
 *       raises an {@link com.qeetgroup.qeetpay.aml.AmlAlert}.</li>
 *   <li><b>Transaction monitoring</b> — a deterministic rules engine ({@link
 *       com.qeetgroup.qeetpay.aml.TransactionMonitor}) flags structuring (amounts just under the CTR
 *       reporting threshold), velocity/geo anomalies, and high-risk MCCs, each producing an alert with
 *       a rule code + risk score.</li>
 *   <li><b>Mule-account detection</b> — {@link com.qeetgroup.qeetpay.aml.MuleScorer} heuristically
 *       scores a payout beneficiary (rapid in-out pass-through, fan-in / fan-out) from a supplied DTO;
 *       it never reads the {@code payouts} tables.</li>
 *   <li><b>STR generation</b> — a {@link com.qeetgroup.qeetpay.aml.StrReport} is generated in an
 *       FIU-IND-style JSON structure (PMLA) and "filed" through {@link
 *       com.qeetgroup.qeetpay.aml.FiuFilingAdapter} (sandbox assigns a reference id).</li>
 * </ul>
 *
 * <p>Alerts and STR filings are published to the transactional outbox for qeet-notify / audit. Money
 * is integer minor units (paise); all arithmetic is exact (no floats).
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "AML",
        allowedDependencies = {"platform", "ledger"})
package com.qeetgroup.qeetpay.aml;
