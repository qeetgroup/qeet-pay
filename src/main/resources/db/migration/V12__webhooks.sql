-- V12 — Outbound webhook engine (TAD Module 05):
--   • webhooks.endpoints — per-merchant registered URLs with signing secrets
--   • webhooks.deliveries — append-only delivery log per endpoint+event

CREATE SCHEMA IF NOT EXISTS webhooks;

CREATE TABLE webhooks.endpoints (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID        NOT NULL REFERENCES platform.merchants (id),
    url            TEXT        NOT NULL,
    events         JSONB       NOT NULL DEFAULT '["*"]',  -- subscribed event types; ["*"] = all
    signing_secret TEXT        NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | DISABLED
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_endpoints_merchant ON webhooks.endpoints (merchant_id);

CREATE TABLE webhooks.deliveries (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_id       UUID        NOT NULL REFERENCES webhooks.endpoints (id),
    merchant_id       UUID        NOT NULL REFERENCES platform.merchants (id),
    event_type        TEXT        NOT NULL,
    payload           JSONB       NOT NULL,
    attempt_count     INT         NOT NULL DEFAULT 0,
    status            TEXT        NOT NULL DEFAULT 'PENDING', -- PENDING | DELIVERED | FAILED
    last_response_code INT,
    last_error        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at      TIMESTAMPTZ
);
CREATE INDEX idx_webhook_deliveries_endpoint ON webhooks.deliveries (endpoint_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_merchant ON webhooks.deliveries (merchant_id, created_at DESC);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['endpoints','deliveries'] LOOP
        EXECUTE format('ALTER TABLE webhooks.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE webhooks.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON webhooks.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

GRANT USAGE ON SCHEMA webhooks TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON webhooks.endpoints TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON webhooks.deliveries TO qeet_pay_app;
