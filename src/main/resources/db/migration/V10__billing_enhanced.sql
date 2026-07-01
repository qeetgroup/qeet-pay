-- V10 — Enhanced subscription billing (TAD Module 03 Phase 1):
--   • billing.plans gains pricing_model, tiers JSONB, usage_metric_key, trial_days
--   • billing.subscriptions gains mandate_id, cancel_at_period_end, trial/pause/cancel timestamps
--   • New billing.usage_events (per-period usage metering)
--   • New billing.subscription_events (append-only audit log)

ALTER TABLE billing.plans
    ADD COLUMN pricing_model    TEXT    NOT NULL DEFAULT 'FLAT',
    ADD COLUMN tiers            JSONB,
    ADD COLUMN usage_metric_key TEXT,
    ADD COLUMN trial_days       INT     NOT NULL DEFAULT 0;

ALTER TABLE billing.subscriptions
    ADD COLUMN mandate_id            UUID REFERENCES mandates.mandates (id),
    ADD COLUMN cancel_at_period_end  BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN trial_ends_at         TIMESTAMPTZ,
    ADD COLUMN paused_at             TIMESTAMPTZ,
    ADD COLUMN cancelled_at          TIMESTAMPTZ;

-- Usage events: append-only; INSERT only for qeet_pay_app.
CREATE TABLE billing.usage_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    subscription_id UUID        NOT NULL REFERENCES billing.subscriptions (id),
    metric_key      TEXT        NOT NULL,
    quantity        BIGINT      NOT NULL CHECK (quantity > 0),
    event_ts        TIMESTAMPTZ NOT NULL DEFAULT now(),
    idempotency_key TEXT
);
CREATE INDEX idx_usage_events_subscription ON billing.usage_events (subscription_id, metric_key, event_ts);
CREATE UNIQUE INDEX idx_usage_events_idem ON billing.usage_events (subscription_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Subscription event audit log: append-only.
CREATE TABLE billing.subscription_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    subscription_id UUID        NOT NULL REFERENCES billing.subscriptions (id),
    event_type      TEXT        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata        JSONB
);
CREATE INDEX idx_sub_events_subscription ON billing.subscription_events (subscription_id, occurred_at);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['usage_events','subscription_events'] LOOP
        EXECUTE format('ALTER TABLE billing.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE billing.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON billing.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

GRANT SELECT, INSERT ON billing.usage_events TO qeet_pay_app;
GRANT SELECT, INSERT ON billing.subscription_events TO qeet_pay_app;
