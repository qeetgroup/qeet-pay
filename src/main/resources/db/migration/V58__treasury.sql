-- V58 — Programmable money & treasury automation (PRD Novel N3, TAD §5). A merchant registers
-- auto-sweep rules that move idle cash between its own ledger accounts; when a rule fires, the sweep
-- is a balanced ledger entry recorded here as an append-only sweep_execution.
--
-- Ledger postings (the sweep itself is posted by ledger.postEntry; this schema only records intent
-- and outcome). For an asset-like source (e.g. settlement, bank):
--   sweep (source -> target):  debit target / credit source   (retaining keep_minor in source)
-- For a liability-like source the directions are reversed. The entry is always balanced.
--
-- sweep_rules mutate (status/updated_at); sweep_executions are append-only.

CREATE SCHEMA IF NOT EXISTS treasury;

-- A per-merchant sweep rule. THRESHOLD rules carry threshold_minor; SCHEDULE rules carry a schedule.
CREATE TABLE treasury.sweep_rules (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    name                 TEXT        NOT NULL,
    source_account_code  TEXT        NOT NULL,
    target_account_code  TEXT        NOT NULL,
    trigger_type         TEXT        NOT NULL,   -- SweepTrigger (THRESHOLD | SCHEDULE)
    threshold_minor      BIGINT      CHECK (threshold_minor IS NULL OR threshold_minor > 0),
    schedule             TEXT,
    keep_minor           BIGINT      NOT NULL DEFAULT 0 CHECK (keep_minor >= 0),
    currency             TEXT        NOT NULL,
    status               TEXT        NOT NULL,   -- SweepRuleStatus (ACTIVE | PAUSED)
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (source_account_code <> target_account_code)
);
CREATE INDEX idx_sweep_rules_merchant ON treasury.sweep_rules (merchant_id);

-- Append-only record of each sweep that fired for a rule, carrying its balanced ledger entry.
CREATE TABLE treasury.sweep_executions (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id                     UUID        NOT NULL REFERENCES treasury.sweep_rules (id),
    merchant_id                 UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor                BIGINT      NOT NULL CHECK (amount_minor > 0),
    source_balance_before_minor BIGINT      NOT NULL,
    ledger_entry_id             UUID        NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sweep_executions_merchant ON treasury.sweep_executions (merchant_id);
CREATE INDEX idx_sweep_executions_rule     ON treasury.sweep_executions (rule_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE treasury.sweep_rules      ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury.sweep_rules      FORCE  ROW LEVEL SECURITY;
ALTER TABLE treasury.sweep_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury.sweep_executions FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON treasury.sweep_rules
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON treasury.sweep_executions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: rules mutate; executions are append-only.
GRANT USAGE ON SCHEMA treasury TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON treasury.sweep_rules      TO qeet_pay_app;
GRANT SELECT, INSERT         ON treasury.sweep_executions TO qeet_pay_app;
