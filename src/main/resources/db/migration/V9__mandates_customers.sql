-- V9 — Mandate management (TAD Module 02): UPI AutoPay + NACH recurring authorizations,
-- and a lightweight customers table used as the canonical customer handle across billing.

-- customers: opaque, merchant-scoped customer records. billing.subscriptions will FK here
-- (added in V10). For now, billing.subscriptions still uses customer_ref TEXT.
CREATE TABLE platform.customers (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id  UUID        NOT NULL REFERENCES platform.merchants (id),
    ref          TEXT        NOT NULL,                -- caller-supplied opaque key (email/user-id)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, ref)
);
CREATE INDEX idx_customers_merchant ON platform.customers (merchant_id);

ALTER TABLE platform.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.customers FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON platform.customers
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON platform.customers TO qeet_pay_app;

-- mandates schema
CREATE SCHEMA IF NOT EXISTS mandates;

CREATE TABLE mandates.mandates (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id       UUID        NOT NULL REFERENCES platform.merchants (id),
    customer_id       UUID        REFERENCES platform.customers (id),
    type              TEXT        NOT NULL CHECK (type IN ('UPI_AUTOPAY','NACH')),
    limit_minor       BIGINT      NOT NULL CHECK (limit_minor > 0),
    currency          TEXT        NOT NULL DEFAULT 'INR',
    frequency         TEXT        NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY','AS_PRESENTED')),
    start_date        DATE        NOT NULL,
    end_date          DATE,
    status            TEXT        NOT NULL DEFAULT 'CREATED'
                                  CHECK (status IN ('CREATED','ACTIVE','PAUSED','REVOKED')),
    provider_mandate_id TEXT,                          -- external mandate ref (Razorpay/NACH)
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mandates_merchant   ON mandates.mandates (merchant_id);
CREATE INDEX idx_mandates_customer   ON mandates.mandates (customer_id);
CREATE INDEX idx_mandates_status     ON mandates.mandates (status);

ALTER TABLE mandates.mandates ENABLE ROW LEVEL SECURITY;
ALTER TABLE mandates.mandates FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON mandates.mandates
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT USAGE ON SCHEMA mandates TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON mandates.mandates TO qeet_pay_app;

-- mandate_debits: immutable log of every debit executed against a mandate
CREATE TABLE mandates.mandate_debits (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mandate_id      UUID        NOT NULL REFERENCES mandates.mandates (id),
    merchant_id     UUID        NOT NULL,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('SUCCEEDED','FAILED')),
    payment_id      UUID,                              -- links to payments.payments on success
    failure_reason  TEXT,
    ledger_entry_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mandate_debits_mandate ON mandates.mandate_debits (mandate_id);

ALTER TABLE mandates.mandate_debits ENABLE ROW LEVEL SECURITY;
ALTER TABLE mandates.mandate_debits FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON mandates.mandate_debits
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- append-only: INSERT only
GRANT SELECT, INSERT ON mandates.mandate_debits TO qeet_pay_app;
