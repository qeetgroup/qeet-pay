-- V4 — Payouts / disbursements (TAD Module 02). Money-out, with a maker-checker control: a payout
-- is created PENDING_APPROVAL and only disburses once approved (separation of create vs approve).
-- On payout it drives a balanced ledger posting (debit liability / credit bank, V2). Mutates
-- (status transitions) so the app role gets UPDATE.

CREATE SCHEMA IF NOT EXISTS payouts;

CREATE TABLE payouts.payouts (
    id                 UUID        PRIMARY KEY,
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor       BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency           TEXT        NOT NULL,
    rail               TEXT        NOT NULL,   -- PayoutRail (UPI, IMPS, NEFT, RTGS)
    destination        TEXT        NOT NULL,   -- VPA or account reference
    status             TEXT        NOT NULL,   -- PayoutStatus (PENDING_APPROVAL, PAID, …)
    provider           TEXT        NOT NULL,
    provider_payout_id TEXT,
    failure_reason     TEXT,
    description        TEXT,
    ledger_entry_id    UUID,                   -- set on payout (ledger.journal_entries.id)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payouts_merchant ON payouts.payouts (merchant_id);

ALTER TABLE payouts.payouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE payouts.payouts FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payouts.payouts
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT USAGE ON SCHEMA payouts TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON payouts.payouts TO qeet_pay_app;
