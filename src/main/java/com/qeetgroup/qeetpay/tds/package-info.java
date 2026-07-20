/**
 * TDS/TCS tracking module (PRD Module 06, GST + Income-Tax compliance, Phase 2). Records tax deducted
 * at source (Income-Tax §§194C/194H/194J and the e-commerce §194-O) and tax collected at source (CGST
 * Act §52 / §206C), derives the Indian financial-year quarter (Apr–Mar) for each entry, issues a
 * deterministic deduction certificate, and produces per-section quarterly summaries. It also prepares
 * the statutory quarterly <em>returns</em> (PRD Module 06.4): aggregating a quarter's deductions into
 * the correct NSDL form (24Q salary / 26Q non-salary / 27EQ TCS), rendering an FVU-style export, and
 * filing to a sandbox TIN gateway that assigns an acknowledgement (provisional receipt number). This
 * is a pure compliance/record-keeping ledger — like {@link com.qeetgroup.qeetpay.filing} it records
 * tax facts and posts <em>no</em> money movement of its own. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "TDS")
package com.qeetgroup.qeetpay.tds;
