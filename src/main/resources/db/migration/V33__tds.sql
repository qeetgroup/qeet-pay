-- V33 — TDS/TCS tracking (PRD Module 06, GST + Income-Tax compliance, Phase 2). Records tax deducted
-- at source (Income-Tax §§194C/194H/194J, §194-O) and tax collected at source (CGST Act §52 / §206C)
-- on individual transactions, derives the Indian financial-year quarter (Apr–Mar) for each entry, and
-- assigns a deduction certificate number once issued to the deductee. This is a compliance *record* —
-- no money moves here (the ledger stays untouched) — so, like the filing worksheet, the app role gets
-- UPDATE (the certificate number is stamped after the fact).

CREATE SCHEMA IF NOT EXISTS tds;

-- One row per tax-at-source fact. tax_minor = gross_minor · rate_bps / 10000 (HALF_UP); quarter is the
-- FY quarter of deducted_on as "<fyStartYear>-Q<n>"; certificate_no is set when the certificate issues.
CREATE TABLE tds.deductions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    kind             TEXT        NOT NULL,   -- TaxKind (TDS | TCS)
    section          TEXT        NOT NULL,   -- statutory section, e.g. 194J | 194O | 52
    deductee_name    TEXT        NOT NULL,
    deductee_pan     TEXT,
    gross_minor      BIGINT      NOT NULL CHECK (gross_minor >= 0),
    rate_bps         INT         NOT NULL CHECK (rate_bps BETWEEN 0 AND 10000),
    tax_minor        BIGINT      NOT NULL CHECK (tax_minor >= 0),
    transaction_ref  TEXT,
    deducted_on      DATE        NOT NULL,
    quarter          TEXT        NOT NULL,   -- FY quarter, e.g. 2026-Q2
    certificate_no   TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tds_deductions_merchant ON tds.deductions (merchant_id);
CREATE INDEX idx_tds_deductions_quarter  ON tds.deductions (merchant_id, quarter);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE tds.deductions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tds.deductions FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON tds.deductions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: deductions mutate once (the certificate number is stamped later).
GRANT USAGE ON SCHEMA tds TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON tds.deductions TO qeet_pay_app;
