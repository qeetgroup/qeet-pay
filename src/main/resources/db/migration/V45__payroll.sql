-- V45 — Payroll disbursement (PRD Module 02.5 & Module 18.4). Qeet People runs a payroll cycle and
-- hands Qeet Pay a batch of employee lines (gross + statutory components PF/ESI/PT/TDS); Qeet Pay
-- computes net pay = gross − statutory, verifies destination accounts (penny-drop, kyb module), and
-- on maker-checker approval disburses each net amount through the existing payouts engine (a bulk
-- payout batch → debit liability / credit bank, V2/V4/V17), capturing the payout + ledger reference
-- and status back onto each line for the combined salary-slip + receipt.
--
-- Both tables mutate (status + counts + captured references on approval) so the app role gets UPDATE.

CREATE SCHEMA IF NOT EXISTS payroll;

CREATE TABLE payroll.payroll_batches (
    id                    UUID        PRIMARY KEY,
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    currency              TEXT        NOT NULL,
    period                TEXT,                   -- payroll cycle, e.g. "2026-07"
    description           TEXT,
    status                TEXT        NOT NULL,   -- PayrollBatchStatus (PENDING_APPROVAL, DISBURSED, …)
    payout_batch_id       UUID,                   -- payouts.payout_batches.id (set on approval)
    line_count            INT         NOT NULL CHECK (line_count > 0),
    total_gross_minor     BIGINT      NOT NULL CHECK (total_gross_minor > 0),
    total_statutory_minor BIGINT      NOT NULL DEFAULT 0 CHECK (total_statutory_minor >= 0),
    total_net_minor       BIGINT      NOT NULL CHECK (total_net_minor > 0),
    paid_count            INT         NOT NULL DEFAULT 0,
    failed_count          INT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_batches_merchant ON payroll.payroll_batches (merchant_id);

CREATE TABLE payroll.payroll_lines (
    id                  UUID        PRIMARY KEY,
    batch_id            UUID        NOT NULL REFERENCES payroll.payroll_batches (id),
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    employee_ref        TEXT        NOT NULL,   -- opaque Qeet People employee reference
    employee_name       TEXT,
    rail                TEXT        NOT NULL,   -- PayoutRail (UPI, IMPS, NEFT, RTGS)
    destination         TEXT        NOT NULL,   -- VPA or account reference the net pay is sent to
    account_number      TEXT,                   -- optional, for penny-drop verification
    ifsc                TEXT,                   -- optional, for penny-drop verification
    verified            BOOLEAN     NOT NULL DEFAULT false,
    verification_result TEXT,
    gross_minor         BIGINT      NOT NULL CHECK (gross_minor > 0),
    pf_minor            BIGINT      NOT NULL DEFAULT 0 CHECK (pf_minor  >= 0),
    esi_minor           BIGINT      NOT NULL DEFAULT 0 CHECK (esi_minor >= 0),
    pt_minor            BIGINT      NOT NULL DEFAULT 0 CHECK (pt_minor  >= 0),
    tds_minor           BIGINT      NOT NULL DEFAULT 0 CHECK (tds_minor >= 0),
    net_pay_minor       BIGINT      NOT NULL CHECK (net_pay_minor > 0),
    status              TEXT        NOT NULL,   -- PayrollLineStatus (PENDING, PAID, FAILED)
    payout_id           UUID,                   -- payouts.payouts.id (set on disbursal)
    ledger_entry_id     UUID,                   -- ledger.journal_entries.id (set on disbursal)
    failure_reason      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payroll_lines_batch    ON payroll.payroll_lines (batch_id);
CREATE INDEX idx_payroll_lines_merchant ON payroll.payroll_lines (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped.
ALTER TABLE payroll.payroll_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll.payroll_batches FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payroll.payroll_batches
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

ALTER TABLE payroll.payroll_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE payroll.payroll_lines FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payroll.payroll_lines
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT USAGE ON SCHEMA payroll TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON payroll.payroll_batches TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON payroll.payroll_lines   TO qeet_pay_app;
