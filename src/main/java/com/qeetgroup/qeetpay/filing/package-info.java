/**
 * GST return filing module (TAD §7.4, PRD Module 06, Phase 2). Aggregates the {@code gst} module's
 * invoices for a tax period into a GSTR-1 (outward-supply detail) or GSTR-3B (summary) return via the
 * gst module's public read API, then files it to GSTN through the pluggable {@link
 * com.qeetgroup.qeetpay.filing.GstnFilingAdapter} to obtain an ARN. Returns are a re-preparable
 * worksheet, not ledger entries. Merchant-scoped via platform RLS.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Filing")
package com.qeetgroup.qeetpay.filing;
