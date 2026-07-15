-- V25 — Virtual accounts (PRD Module 01 "Virtual Accounts", Phase 2). A unique account number + IFSC
-- minted per customer so inbound bank/UPI credits auto-reconcile to the right merchant+customer
-- without a manual match. An inbound credit is an inbound collection, so it posts the canonical
-- money-in entry (debit settlement / credit revenue), identical to a payment capture, and is
-- idempotent on the bank UTR so a replayed webhook never double-credits.
--
-- accounts mutate (status ACTIVE -> CLOSED); credits are append-only.

CREATE SCHEMA IF NOT EXISTS virtualaccounts;

-- A virtual account assigned to one of a merchant's customers.
CREATE TABLE virtualaccounts.virtual_accounts (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    customer_ref  TEXT        NOT NULL,
    va_number     TEXT        NOT NULL,
    ifsc          TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',  -- VirtualAccountStatus (ACTIVE | CLOSED)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ,
    UNIQUE (merchant_id, va_number)
);
CREATE INDEX idx_virtual_accounts_merchant ON virtualaccounts.virtual_accounts (merchant_id);
CREATE INDEX idx_virtual_accounts_customer ON virtualaccounts.virtual_accounts (merchant_id, customer_ref);

-- An inbound credit to a virtual account, auto-reconciled to the ledger on arrival. Idempotent by UTR.
CREATE TABLE virtualaccounts.virtual_account_credits (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    va_id           UUID        NOT NULL REFERENCES virtualaccounts.virtual_accounts (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    utr             TEXT        NOT NULL,   -- bank reference; unique per merchant for idempotency
    payer_name      TEXT,
    payer_ref       TEXT,                   -- payer VPA / account, for audit
    ledger_entry_id UUID        NOT NULL,   -- the money-in posting (ledger.journal_entries.id)
    credited_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, utr)
);
CREATE INDEX idx_va_credits_va ON virtualaccounts.virtual_account_credits (va_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE virtualaccounts.virtual_accounts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE virtualaccounts.virtual_accounts        FORCE  ROW LEVEL SECURITY;
ALTER TABLE virtualaccounts.virtual_account_credits ENABLE ROW LEVEL SECURITY;
ALTER TABLE virtualaccounts.virtual_account_credits FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON virtualaccounts.virtual_accounts
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON virtualaccounts.virtual_account_credits
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: accounts mutate (status); credits are append-only.
GRANT USAGE ON SCHEMA virtualaccounts TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON virtualaccounts.virtual_accounts        TO qeet_pay_app;
GRANT SELECT, INSERT         ON virtualaccounts.virtual_account_credits TO qeet_pay_app;
