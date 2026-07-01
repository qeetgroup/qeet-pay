-- V14 — GST Credit/Debit Notes + Multi-GSTIN support (TAD Module 08):
--   • gst.gst_notes — invoice corrections (CREDIT_NOTE | DEBIT_NOTE)
--   • platform.merchant_gstins — additional GSTINs per merchant
--   • gst.gst_invoices gains optional merchant_gstin_id FK

CREATE TABLE platform.merchant_gstins (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    gstin       TEXT        NOT NULL,
    legal_name  TEXT        NOT NULL,
    state_code  TEXT        NOT NULL,   -- 2-digit state code
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, gstin)
);
CREATE INDEX idx_merchant_gstins_merchant ON platform.merchant_gstins (merchant_id);

-- Ensure at most one default GSTIN per merchant
CREATE UNIQUE INDEX idx_merchant_gstins_default
    ON platform.merchant_gstins (merchant_id)
    WHERE is_default = true;

ALTER TABLE platform.merchant_gstins ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.merchant_gstins FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON platform.merchant_gstins
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON platform.merchant_gstins TO qeet_pay_app;

-- Optional FK from gst_invoices to the issuing GSTIN entity (nullable — legacy rows use supplier_gstin TEXT)
ALTER TABLE gst.gst_invoices
    ADD COLUMN merchant_gstin_id UUID REFERENCES platform.merchant_gstins (id);

-- Credit / Debit Notes
CREATE TABLE gst.gst_notes (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    type                TEXT        NOT NULL,   -- CREDIT_NOTE | DEBIT_NOTE
    original_invoice_id UUID        NOT NULL REFERENCES gst.gst_invoices (id),
    reason              TEXT        NOT NULL,
    taxable_minor       BIGINT      NOT NULL,
    cgst_minor          BIGINT      NOT NULL DEFAULT 0,
    sgst_minor          BIGINT      NOT NULL DEFAULT 0,
    igst_minor          BIGINT      NOT NULL DEFAULT 0,
    total_minor         BIGINT      NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'ISSUED',  -- ISSUED | APPLIED | CANCELLED
    ledger_entry_id     UUID,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_gst_notes_merchant ON gst.gst_notes (merchant_id);
CREATE INDEX idx_gst_notes_invoice  ON gst.gst_notes (original_invoice_id);

ALTER TABLE gst.gst_notes ENABLE ROW LEVEL SECURITY;
ALTER TABLE gst.gst_notes FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON gst.gst_notes
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON gst.gst_notes TO qeet_pay_app;
