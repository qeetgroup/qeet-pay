-- V47 — Accounting integrations (PRD Module 11.3). Export a period's ledger journal entries + GST
-- invoices to an external accounting system (Tally Prime import XML / Zoho Books / a generic
-- webhook). Each run is recorded in accounting_syncs (target, period, status, record_count,
-- external_ref, plus the generated document so it can be re-downloaded). Per-merchant, per-target
-- connection settings live in accounting_connections (Zoho creds themselves come from app config,
-- never persisted). This module is read-only against ledger.* / gst.* — it never posts to the ledger.

CREATE SCHEMA IF NOT EXISTS accounting;

CREATE TABLE accounting.accounting_syncs (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID        NOT NULL REFERENCES platform.merchants (id),
    target         TEXT        NOT NULL,             -- AccountingTarget (TALLY | ZOHO | WEBHOOK)
    period_start   TIMESTAMPTZ NOT NULL,
    period_end     TIMESTAMPTZ NOT NULL,
    status         TEXT        NOT NULL,             -- SyncStatus (PENDING | SUCCESS | FAILED)
    record_count   INT         NOT NULL DEFAULT 0,
    external_ref   TEXT,                             -- provider id / filename (target-specific)
    detail         TEXT,                             -- failure reason / note
    document       TEXT,                             -- generated export artifact (Tally XML / JSON)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at   TIMESTAMPTZ
);
CREATE INDEX idx_accounting_syncs_merchant ON accounting.accounting_syncs (merchant_id);
CREATE INDEX idx_accounting_syncs_created  ON accounting.accounting_syncs (merchant_id, created_at);

CREATE TABLE accounting.accounting_connections (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    target                TEXT        NOT NULL,      -- AccountingTarget (TALLY | ZOHO | WEBHOOK)
    enabled               BOOLEAN     NOT NULL DEFAULT true,
    webhook_url           TEXT,                      -- WEBHOOK target endpoint
    zoho_organization_id  TEXT,                      -- ZOHO per-merchant org id (creds come from config)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, target)
);
CREATE INDEX idx_accounting_connections_merchant ON accounting.accounting_connections (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1; copied from V19__marketplace.sql).
-- FORCE so the table owner is scoped too; current_setting(..., true) hides rows when unset.
ALTER TABLE accounting.accounting_syncs        ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounting.accounting_syncs        FORCE  ROW LEVEL SECURITY;
ALTER TABLE accounting.accounting_connections  ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounting.accounting_connections  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON accounting.accounting_syncs
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON accounting.accounting_connections
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both tables mutate (sync status transitions; connection upserts).
GRANT USAGE ON SCHEMA accounting TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON accounting.accounting_syncs       TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON accounting.accounting_connections TO qeet_pay_app;
