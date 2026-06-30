-- V5 — Subscription billing (TAD Module 03): plans, subscriptions, and invoices. Phase 1 recognises
-- revenue on a cash basis — paying an invoice posts a balanced ledger entry (debit settlement /
-- credit revenue, V2). Accrual / deferred-revenue (IndAS 115) is Phase 2. All merchant-scoped (RLS).

CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.plans (
    id           UUID        PRIMARY KEY,
    merchant_id  UUID        NOT NULL REFERENCES platform.merchants (id),
    code         TEXT        NOT NULL,
    name         TEXT        NOT NULL,
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency     TEXT        NOT NULL,
    interval     TEXT        NOT NULL,   -- BillingInterval (MONTH, YEAR)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, code)
);
CREATE INDEX idx_plans_merchant ON billing.plans (merchant_id);

CREATE TABLE billing.subscriptions (
    id                   UUID        PRIMARY KEY,
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    plan_id              UUID        NOT NULL REFERENCES billing.plans (id),
    customer_ref         TEXT        NOT NULL,
    status               TEXT        NOT NULL,   -- SubscriptionStatus (ACTIVE, CANCELLED)
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_merchant ON billing.subscriptions (merchant_id);

CREATE TABLE billing.invoices (
    id              UUID        PRIMARY KEY,
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    subscription_id UUID        NOT NULL REFERENCES billing.subscriptions (id),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    status          TEXT        NOT NULL,   -- InvoiceStatus (OPEN, PAID, VOID)
    ledger_entry_id UUID,                   -- set on pay (ledger.journal_entries.id)
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ
);
CREATE INDEX idx_invoices_merchant ON billing.invoices (merchant_id);
CREATE INDEX idx_invoices_subscription ON billing.invoices (subscription_id);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['plans','subscriptions','invoices'] LOOP
        EXECUTE format('ALTER TABLE billing.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE billing.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON billing.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

GRANT USAGE ON SCHEMA billing TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA billing TO qeet_pay_app;
