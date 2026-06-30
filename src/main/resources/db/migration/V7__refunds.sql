-- V7 — Refunds (TAD Module 02 / §4). A refund reverses (fully or partially) a captured payment and
-- posts an offsetting ledger entry (debit revenue / credit settlement — the reverse of capture).
-- Refunds are append-only (INSERT-only for the app role), like ledger entries.

CREATE TABLE payments.refunds (
    id                 UUID        PRIMARY KEY,
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    payment_id         UUID        NOT NULL REFERENCES payments.payments (id),
    amount_minor       BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency           TEXT        NOT NULL,
    status             TEXT        NOT NULL,   -- RefundStatus (SUCCEEDED, FAILED)
    provider_refund_id TEXT,
    reason             TEXT,
    ledger_entry_id    UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_merchant ON payments.refunds (merchant_id);
CREATE INDEX idx_refunds_payment ON payments.refunds (payment_id);

ALTER TABLE payments.refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments.refunds FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payments.refunds
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT ON payments.refunds TO qeet_pay_app;  -- append-only
