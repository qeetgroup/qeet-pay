-- Analytics schema: append-only event tables for TPV, MRR waterfall, ARR.
-- TimescaleDB hypertables are created if the extension is available (production);
-- falls back to regular Postgres tables in test/dev containers transparently.

CREATE SCHEMA IF NOT EXISTS analytics;

-- Try to enable TimescaleDB; no-op + silent if not installed.
DO $$
BEGIN
  CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE;
EXCEPTION WHEN OTHERS THEN NULL;
END;
$$;

GRANT USAGE ON SCHEMA analytics TO qeet_pay_app;

-- Payment-level analytics
CREATE TABLE analytics.payment_events (
  id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id    UUID        NOT NULL,
  payment_id     UUID        NOT NULL,
  event_type     TEXT        NOT NULL,   -- CAPTURED | FAILED | REFUNDED
  amount_minor   BIGINT      NOT NULL DEFAULT 0,
  method         TEXT,
  occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Subscription MRR-delta events (waterfall components)
CREATE TABLE analytics.subscription_events (
  id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id      UUID        NOT NULL,
  subscription_id  UUID        NOT NULL,
  event_type       TEXT        NOT NULL,  -- NEW | EXPANSION | CONTRACTION | CHURN | REACTIVATION
  mrr_delta_minor  BIGINT      NOT NULL DEFAULT 0,  -- signed: negative for CHURN/CONTRACTION
  occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Promote to hypertables when TimescaleDB is present.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
    PERFORM create_hypertable('analytics.payment_events',    'occurred_at', if_not_exists => true);
    PERFORM create_hypertable('analytics.subscription_events','occurred_at', if_not_exists => true);
  END IF;
END;
$$;

-- Indexes (effective on both regular tables and TimescaleDB chunks)
CREATE INDEX idx_pe_merchant_occurred ON analytics.payment_events (merchant_id, occurred_at DESC);
CREATE INDEX idx_pe_type              ON analytics.payment_events (merchant_id, event_type, occurred_at DESC);
CREATE INDEX idx_se_merchant_occurred ON analytics.subscription_events (merchant_id, occurred_at DESC);

-- RLS (append-only: qeet_pay_app gets SELECT + INSERT only)
ALTER TABLE analytics.payment_events      ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics.subscription_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY pe_merchant_isolate ON analytics.payment_events TO qeet_pay_app
  USING (merchant_id::text = current_setting('app.current_merchant_id', true));
CREATE POLICY se_merchant_isolate ON analytics.subscription_events TO qeet_pay_app
  USING (merchant_id::text = current_setting('app.current_merchant_id', true));

GRANT SELECT, INSERT ON analytics.payment_events      TO qeet_pay_app;
GRANT SELECT, INSERT ON analytics.subscription_events TO qeet_pay_app;
