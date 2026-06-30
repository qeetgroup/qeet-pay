/**
 * GST module — GST-compliant invoicing (TAD Module 05 / §7.2): CGST+SGST (intra-state) vs IGST
 * (inter-state) from place-of-supply, HSN/SAC, FY-aware sequential numbering. Paying an invoice
 * posts a 3-line entry (debit settlement / credit revenue / credit tax_payable) via the {@code
 * ledger} module. IRN/e-invoicing + GSTR-1 filing are Phase 2. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "GST")
package com.qeetgroup.qeetpay.gst;
