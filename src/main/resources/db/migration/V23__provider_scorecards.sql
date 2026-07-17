-- V23 — Smart payment orchestration: provider scorecards (PRD Module 07.3, TAD §5.1 / §7 Phase-2
-- routing). The rule-based router (V8) keeps working; this adds a per-merchant, per-provider
-- scorecard that the router updates after every authorize/capture/refund and consults when choosing
-- an acquirer: a rolling authorization rate, a health signal (consecutive failures → DEGRADED → DOWN),
-- and a cost basis so the scorer can trade auth-rate against cost. Scorecards mutate on every call.

CREATE TABLE payments.provider_scorecards (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    provider             TEXT        NOT NULL,   -- SANDBOX | RAZORPAY | CASHFREE | …
    attempts             BIGINT      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    successes            BIGINT      NOT NULL DEFAULT 0 CHECK (successes >= 0),
    failures             BIGINT      NOT NULL DEFAULT 0 CHECK (failures >= 0),
    consecutive_failures INT         NOT NULL DEFAULT 0 CHECK (consecutive_failures >= 0),
    cost_bps             INT         NOT NULL DEFAULT 0 CHECK (cost_bps BETWEEN 0 AND 10000),
    health               TEXT        NOT NULL DEFAULT 'HEALTHY',  -- ProviderHealth (HEALTHY | DEGRADED | DOWN)
    last_outcome_at      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, provider)
);
CREATE INDEX idx_provider_scorecards_merchant ON payments.provider_scorecards (merchant_id);

ALTER TABLE payments.provider_scorecards ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments.provider_scorecards FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON payments.provider_scorecards
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON payments.provider_scorecards TO qeet_pay_app;
