-- V21 — GST return filing (TAD §7.4, PRD Module 06, Phase 2). Aggregates a merchant's GST invoices
-- for a tax period (YYYY-MM) into a GSTR-1 (outward-supply detail, with per-invoice lines) or GSTR-3B
-- (outward-supply summary) return, then files it to GSTN through a pluggable adapter which returns an
-- ARN (acknowledgement reference number). Returns are a filing *worksheet* — re-preparable until
-- filed — not ledger entries, so the app role gets UPDATE/DELETE here (the ledger stays append-only).

CREATE SCHEMA IF NOT EXISTS filing;

-- One return per (merchant, type, period). Totals are the sum of the period's invoices; gstn_arn is
-- set once GSTN accepts the filing. Lifecycle: DRAFT/PREPARED → FILED (or ERROR).
CREATE TABLE filing.gst_returns (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    return_type          TEXT        NOT NULL,   -- GstReturnType (GSTR1 | GSTR3B)
    period               TEXT        NOT NULL,   -- tax period, YYYY-MM
    status               TEXT        NOT NULL,   -- GstReturnStatus (DRAFT | PREPARED | FILED | ERROR)
    invoice_count        INT         NOT NULL DEFAULT 0,
    total_taxable_minor  BIGINT      NOT NULL DEFAULT 0,
    total_cgst_minor     BIGINT      NOT NULL DEFAULT 0,
    total_sgst_minor     BIGINT      NOT NULL DEFAULT 0,
    total_igst_minor     BIGINT      NOT NULL DEFAULT 0,
    total_tax_minor      BIGINT      NOT NULL DEFAULT 0,
    gstn_arn             TEXT,
    prepared_at          TIMESTAMPTZ,
    filed_at             TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, return_type, period)
);
CREATE INDEX idx_gst_returns_merchant ON filing.gst_returns (merchant_id);

-- The B2B/B2C outward-supply detail rows of a GSTR-1 return (one per invoice in the period).
CREATE TABLE filing.gst_return_lines (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id       UUID        NOT NULL REFERENCES filing.gst_returns (id) ON DELETE CASCADE,
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    invoice_id      UUID        NOT NULL,
    invoice_number  TEXT        NOT NULL,
    buyer_gstin     TEXT,
    place_of_supply TEXT        NOT NULL,
    supply_type     TEXT        NOT NULL,   -- INTRA_STATE | INTER_STATE
    taxable_minor   BIGINT      NOT NULL,
    cgst_minor      BIGINT      NOT NULL,
    sgst_minor      BIGINT      NOT NULL,
    igst_minor      BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_gst_return_lines_return ON filing.gst_return_lines (return_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE filing.gst_returns      ENABLE ROW LEVEL SECURITY;
ALTER TABLE filing.gst_returns      FORCE  ROW LEVEL SECURITY;
ALTER TABLE filing.gst_return_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE filing.gst_return_lines FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON filing.gst_returns
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON filing.gst_return_lines
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: returns are a re-preparable worksheet (UPDATE + line DELETE on re-prep).
GRANT USAGE ON SCHEMA filing TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE         ON filing.gst_returns      TO qeet_pay_app;
GRANT SELECT, INSERT, DELETE         ON filing.gst_return_lines TO qeet_pay_app;
