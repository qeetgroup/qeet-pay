-- V27 — Cross-border / export collection (PRD Module 14, TAD §5 "Cross-Border", Phase 2/3). An Indian
-- exporter raises a foreign-currency export invoice (zero-rated under LUT/bond, with a FEMA/RBI
-- purpose code); when the foreign inward remittance arrives it is converted to INR at the captured FX
-- rate, the FIRA (Foreign Inward Remittance Advice) reference is recorded, and the INR equivalent is
-- posted to the ledger as money-in (debit settlement / credit revenue).
--
-- export_invoices mutate (status ISSUED -> REMITTED); remittances are append-only.

CREATE SCHEMA IF NOT EXISTS crossborder;

-- A foreign-currency export invoice. amounts are in the foreign currency's minor units (e.g. cents).
CREATE TABLE crossborder.export_invoices (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    invoice_number        TEXT        NOT NULL,
    buyer_country         TEXT        NOT NULL,   -- ISO country code
    currency              TEXT        NOT NULL,   -- foreign currency (USD, EUR, GBP, …)
    foreign_amount_minor  BIGINT      NOT NULL CHECK (foreign_amount_minor > 0),
    purpose_code          TEXT        NOT NULL,   -- FEMA/RBI purpose code (e.g. P0802)
    lut                   BOOLEAN     NOT NULL DEFAULT TRUE,  -- export under LUT/bond (zero-rated GST)
    status                TEXT        NOT NULL,   -- ExportInvoiceStatus (ISSUED | REMITTED)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, invoice_number)
);
CREATE INDEX idx_export_invoices_merchant ON crossborder.export_invoices (merchant_id);

-- A foreign inward remittance settling an export invoice, with the captured FX rate + FIRA reference.
CREATE TABLE crossborder.inward_remittances (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    export_invoice_id     UUID          NOT NULL REFERENCES crossborder.export_invoices (id),
    merchant_id           UUID          NOT NULL REFERENCES platform.merchants (id),
    foreign_amount_minor  BIGINT        NOT NULL CHECK (foreign_amount_minor > 0),
    foreign_currency      TEXT          NOT NULL,
    fx_rate               NUMERIC(18,6) NOT NULL CHECK (fx_rate > 0),  -- INR per 1 unit of foreign currency
    inr_amount_minor      BIGINT        NOT NULL CHECK (inr_amount_minor > 0),
    fira_reference        TEXT          NOT NULL,   -- FIRA / e-FIRA document reference
    purpose_code          TEXT          NOT NULL,
    ledger_entry_id       UUID          NOT NULL,   -- the money-in posting (ledger.journal_entries.id)
    remitted_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_inward_remittances_invoice ON crossborder.inward_remittances (export_invoice_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE crossborder.export_invoices     ENABLE ROW LEVEL SECURITY;
ALTER TABLE crossborder.export_invoices     FORCE  ROW LEVEL SECURITY;
ALTER TABLE crossborder.inward_remittances  ENABLE ROW LEVEL SECURITY;
ALTER TABLE crossborder.inward_remittances  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON crossborder.export_invoices
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON crossborder.inward_remittances
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: export invoices mutate (status); remittances are append-only.
GRANT USAGE ON SCHEMA crossborder TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON crossborder.export_invoices    TO qeet_pay_app;
GRANT SELECT, INSERT         ON crossborder.inward_remittances TO qeet_pay_app;
