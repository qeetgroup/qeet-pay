-- V6 — GST-compliant invoicing (TAD Module 05 / §7.2, PRD 5.1). Generates GST invoices with
-- CGST+SGST (intra-state) vs IGST (inter-state) from place-of-supply, HSN/SAC, and FY-aware
-- sequential numbering. Paying an invoice posts a 3-line balanced ledger entry (debit settlement /
-- credit revenue / credit tax_payable). IRN/e-invoicing + GSTR-1 filing are Phase 2.

CREATE SCHEMA IF NOT EXISTS gst;

CREATE TABLE gst.gst_invoices (
    id               UUID        PRIMARY KEY,
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    invoice_number   TEXT        NOT NULL,          -- FY-aware, e.g. QP/2026-27/00001
    supplier_gstin   TEXT        NOT NULL,
    buyer_gstin      TEXT,
    place_of_supply  TEXT        NOT NULL,          -- 2-digit state code
    supply_type      TEXT        NOT NULL,          -- SupplyType (INTRA_STATE, INTER_STATE)
    currency         TEXT        NOT NULL,
    status           TEXT        NOT NULL,          -- GstInvoiceStatus (ISSUED, PAID, CANCELLED)
    taxable_minor    BIGINT      NOT NULL,
    cgst_minor       BIGINT      NOT NULL,
    sgst_minor       BIGINT      NOT NULL,
    igst_minor       BIGINT      NOT NULL,
    total_gst_minor  BIGINT      NOT NULL,
    total_minor      BIGINT      NOT NULL,
    ledger_entry_id  UUID,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at          TIMESTAMPTZ,
    UNIQUE (merchant_id, invoice_number)
);
CREATE INDEX idx_gst_invoices_merchant ON gst.gst_invoices (merchant_id);

CREATE TABLE gst.gst_invoice_lines (
    id               UUID        PRIMARY KEY,
    invoice_id       UUID        NOT NULL REFERENCES gst.gst_invoices (id),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    description      TEXT        NOT NULL,
    hsn_sac          TEXT        NOT NULL,
    quantity         BIGINT      NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT      NOT NULL CHECK (unit_price_minor > 0),
    gst_rate         INT         NOT NULL CHECK (gst_rate >= 0),
    taxable_minor    BIGINT      NOT NULL,
    cgst_minor       BIGINT      NOT NULL,
    sgst_minor       BIGINT      NOT NULL,
    igst_minor       BIGINT      NOT NULL,
    line_total_minor BIGINT      NOT NULL
);
CREATE INDEX idx_gst_invoice_lines_invoice ON gst.gst_invoice_lines (invoice_id);

-- FY-aware sequential numbering source (one row per merchant per fiscal year).
CREATE TABLE gst.invoice_counters (
    id          UUID        PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    fiscal_year TEXT        NOT NULL,
    last_seq    BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (merchant_id, fiscal_year)
);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['gst_invoices','gst_invoice_lines','invoice_counters'] LOOP
        EXECUTE format('ALTER TABLE gst.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE gst.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON gst.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

GRANT USAGE ON SCHEMA gst TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA gst TO qeet_pay_app;
