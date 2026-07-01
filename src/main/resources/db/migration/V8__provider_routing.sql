-- V8 — Payment provider routing (TAD §7 orchestration). Two tables:
--   provider_configs  : per-merchant provider preferences (queried in Phase 2 per-merchant routing).
--   provider_transactions: immutable audit log of every provider API call.

CREATE TABLE payments.provider_configs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    provider_name TEXT        NOT NULL CHECK (provider_name IN ('SANDBOX','RAZORPAY','CASHFREE')),
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    priority      INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE payments.provider_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments.provider_configs FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payments.provider_configs
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON payments.provider_configs TO qeet_pay_app;

-- Append-only audit of every authorize / capture / refund call to a provider.
-- Linked to the payment that triggered it; provider_ref is the external ID returned.
CREATE TABLE payments.provider_transactions (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   UUID        NOT NULL REFERENCES payments.payments (id),
    merchant_id  UUID        NOT NULL,
    provider_name TEXT       NOT NULL,
    operation    TEXT        NOT NULL CHECK (operation IN ('AUTHORIZE','CAPTURE','REFUND')),
    provider_ref TEXT,                   -- order_id / payment_id / refund_id returned by provider
    success      BOOLEAN     NOT NULL,
    raw_response TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_provider_txns_payment ON payments.provider_transactions (payment_id);

ALTER TABLE payments.provider_transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments.provider_transactions FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payments.provider_transactions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Append-only: INSERT only.
GRANT SELECT, INSERT ON payments.provider_transactions TO qeet_pay_app;
