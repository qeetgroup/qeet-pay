-- V34 — Input Tax Credit / GSTR-2B reconciliation (PRD Module 05/06, Phase 2). Records a merchant's
-- INWARD supplies (purchase invoices received from suppliers) and reconciles each against the
-- supplier-filed GSTR-2B pulled from the GST portal, reporting the ITC the merchant can claim. This is
-- pure compliance tracking — no ledger entries, no money movement. The recon_status column mutates as
-- 2B runs land, so the app role gets UPDATE here (the ledger stays append-only).

CREATE SCHEMA IF NOT EXISTS itc;

-- One row per inward-supply purchase invoice. total_gst_minor = cgst + sgst + igst (derived in the
-- domain). recon_status starts UNMATCHED and is resolved by a GSTR-2B run; reconciled_at is set then.
CREATE TABLE itc.purchase_invoices (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    supplier_gstin   TEXT        NOT NULL,
    supplier_name    TEXT        NOT NULL,
    invoice_number   TEXT        NOT NULL,
    invoice_date     DATE        NOT NULL,
    taxable_minor    BIGINT      NOT NULL CHECK (taxable_minor >= 0),
    cgst_minor       BIGINT      NOT NULL CHECK (cgst_minor >= 0),
    sgst_minor       BIGINT      NOT NULL CHECK (sgst_minor >= 0),
    igst_minor       BIGINT      NOT NULL CHECK (igst_minor >= 0),
    total_gst_minor  BIGINT      NOT NULL CHECK (total_gst_minor >= 0),
    itc_eligible     BOOLEAN     NOT NULL DEFAULT TRUE,
    recon_status     TEXT        NOT NULL,   -- ReconStatus (UNMATCHED | MATCHED | MISMATCHED | MISSING_IN_2B)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    reconciled_at    TIMESTAMPTZ
);
CREATE INDEX idx_purchase_invoices_merchant ON itc.purchase_invoices (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE itc.purchase_invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE itc.purchase_invoices FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON itc.purchase_invoices
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: recon_status mutates as 2B runs land, so UPDATE is granted.
GRANT USAGE ON SCHEMA itc TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON itc.purchase_invoices TO qeet_pay_app;
