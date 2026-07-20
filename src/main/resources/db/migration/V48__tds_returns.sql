-- V48 — TDS/TCS statutory quarterly returns (PRD Module 06.4, Income-Tax compliance, Phase 2).
-- Aggregates a merchant's tax-at-source facts (tds.deductions, V33) for one FY quarter into the
-- correct NSDL form — 24Q (TDS on salary §192), 26Q (TDS on non-salary payments), or 27EQ (TCS) —
-- with the consolidated deposit challan (BSR + serial), the deductee/collectee detail rows, and,
-- once filed at the sandbox TIN gateway, the acknowledgement (provisional receipt number). Returns
-- are a filing *worksheet* — re-preparable until filed, so the app role gets UPDATE/DELETE here — and
-- move no money (the ledger stays append-only). The tds schema was created in V33.

CREATE SCHEMA IF NOT EXISTS tds;

-- One return per (merchant, form, financial year, quarter). Totals are the sum of the quarter's
-- kept deductions; ack_token is set once the return is filed. Lifecycle: DRAFT/PREPARED → FILED.
CREATE TABLE tds.returns (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    form               TEXT        NOT NULL,   -- TdsReturnForm (FORM_24Q | FORM_26Q | FORM_27EQ)
    fy                 TEXT        NOT NULL,   -- assessment financial year, e.g. 2026-27
    quarter            TEXT        NOT NULL,   -- FY quarter label, Q1..Q4
    status             TEXT        NOT NULL,   -- TdsReturnStatus (DRAFT | PREPARED | FILED)
    deductee_count     INT         NOT NULL DEFAULT 0,
    deduction_count    INT         NOT NULL DEFAULT 0,
    total_gross_minor  BIGINT      NOT NULL DEFAULT 0 CHECK (total_gross_minor >= 0),
    total_tax_minor    BIGINT      NOT NULL DEFAULT 0 CHECK (total_tax_minor >= 0),
    bsr_code           TEXT,       -- collecting-bank BSR code of the deposit challan
    challan_no         TEXT,       -- challan serial number
    challan_date       DATE,
    ack_token          TEXT,       -- provisional receipt number (assigned on filing)
    prepared_at        TIMESTAMPTZ,
    filed_at           TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, form, fy, quarter)
);
CREATE INDEX idx_tds_returns_merchant ON tds.returns (merchant_id);

-- The deductee/collectee detail rows of a return (one per tax-at-source transaction in the quarter),
-- projected from tds.deductions at prepare time.
CREATE TABLE tds.return_lines (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id        UUID        NOT NULL REFERENCES tds.returns (id) ON DELETE CASCADE,
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    deduction_id     UUID        NOT NULL,
    deductee_name    TEXT        NOT NULL,
    deductee_pan     TEXT,
    section          TEXT        NOT NULL,
    gross_minor      BIGINT      NOT NULL CHECK (gross_minor >= 0),
    rate_bps         INT         NOT NULL CHECK (rate_bps BETWEEN 0 AND 10000),
    tax_minor        BIGINT      NOT NULL CHECK (tax_minor >= 0),
    deducted_on      DATE        NOT NULL,
    transaction_ref  TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tds_return_lines_return ON tds.return_lines (return_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE tds.returns      ENABLE ROW LEVEL SECURITY;
ALTER TABLE tds.returns      FORCE  ROW LEVEL SECURITY;
ALTER TABLE tds.return_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE tds.return_lines FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON tds.returns
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON tds.return_lines
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: returns are a re-preparable worksheet (UPDATE + line DELETE on re-prep).
GRANT USAGE ON SCHEMA tds TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON tds.returns      TO qeet_pay_app;
GRANT SELECT, INSERT, DELETE ON tds.return_lines TO qeet_pay_app;
