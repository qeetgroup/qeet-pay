-- V11 — Rule-based dunning engine (TAD Module 04):
--   • dunning.rules — merchant-configured retry policies
--   • dunning.attempts — append-only per-attempt audit (INSERT only for qeet_pay_app)

CREATE SCHEMA IF NOT EXISTS dunning;

CREATE TABLE dunning.rules (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    name                  TEXT        NOT NULL,
    failure_code_pattern  TEXT        NOT NULL DEFAULT '*',   -- wildcard, '*' = catch-all
    retry_interval_hours  INT         NOT NULL DEFAULT 24,
    max_attempts          INT         NOT NULL DEFAULT 3,
    notify_channels       JSONB,                               -- ["EMAIL","SMS","WHATSAPP"]
    active                BOOLEAN     NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dunning_rules_merchant ON dunning.rules (merchant_id);

CREATE TABLE dunning.attempts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    subscription_id UUID        NOT NULL REFERENCES billing.subscriptions (id),
    rule_id         UUID        REFERENCES dunning.rules (id),
    attempt_number  INT         NOT NULL,
    scheduled_at    TIMESTAMPTZ NOT NULL,
    attempted_at    TIMESTAMPTZ,
    result          TEXT,           -- SUCCESS | FAILED | SKIPPED
    failure_reason  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dunning_attempts_subscription ON dunning.attempts (subscription_id, attempt_number);

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['rules','attempts'] LOOP
        EXECUTE format('ALTER TABLE dunning.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE dunning.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON dunning.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

GRANT USAGE ON SCHEMA dunning TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON dunning.rules TO qeet_pay_app;
GRANT SELECT, INSERT ON dunning.attempts TO qeet_pay_app;
