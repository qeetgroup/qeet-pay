-- V3 — Payment acceptance (TAD Module 01 / §4.1). A payment is created, authorized via a provider,
-- then captured; on capture it drives a balanced double-entry ledger posting (settlement debit /
-- revenue credit, V2). Unlike the append-only ledger, payments mutate (status transitions), so the
-- app role gets UPDATE here.

CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE payments.payments (
    id                  UUID        PRIMARY KEY,
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor        BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency            TEXT        NOT NULL,
    method              TEXT        NOT NULL,   -- PaymentMethod (UPI, CARD, NET_BANKING, WALLET)
    status              TEXT        NOT NULL,   -- PaymentStatus (CREATED, AUTHORIZED, CAPTURED, …)
    provider            TEXT        NOT NULL,
    provider_payment_id TEXT,
    failure_reason      TEXT,
    description         TEXT,
    ledger_entry_id     UUID,                   -- set on capture (ledger.journal_entries.id)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_merchant ON payments.payments (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1).
ALTER TABLE payments.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments.payments FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payments.payments
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT USAGE ON SCHEMA payments TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON payments.payments TO qeet_pay_app;
