-- V17 — Bulk payouts (TAD §17 Phase-1: "bulk payouts via API or CSV upload" + "maker-checker
-- approval workflow for bulk disbursals"). A batch groups many payouts created together and
-- approved/rejected as one unit; approving disburses each member (debit liability / credit bank, V2)
-- and the batch records the aggregate outcome (all paid, partial, or all failed).
--
-- Batches mutate (status + counts) so the app role gets UPDATE. payouts.payouts gains a nullable
-- batch_id — single payouts leave it null; the existing table grant already covers the new column.

CREATE TABLE payouts.payout_batches (
    id                 UUID        PRIMARY KEY,
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    currency           TEXT        NOT NULL,
    description        TEXT,
    status             TEXT        NOT NULL,   -- BatchStatus (PENDING_APPROVAL, COMPLETED, …)
    total_count        INT         NOT NULL,
    total_amount_minor BIGINT      NOT NULL CHECK (total_amount_minor > 0),
    paid_count         INT         NOT NULL DEFAULT 0,
    failed_count       INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payout_batches_merchant ON payouts.payout_batches (merchant_id);

ALTER TABLE payouts.payouts ADD COLUMN batch_id UUID REFERENCES payouts.payout_batches (id);
CREATE INDEX idx_payouts_batch ON payouts.payouts (batch_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped.
ALTER TABLE payouts.payout_batches ENABLE ROW LEVEL SECURITY;
ALTER TABLE payouts.payout_batches FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payouts.payout_batches
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON payouts.payout_batches TO qeet_pay_app;
