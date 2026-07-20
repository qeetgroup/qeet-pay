-- V53 — Fraud decision audit (PRD Module 08.1/8.4 — real-time ML scoring + Explainable AI).
-- Persists one merchant-scoped row per fraud-scoring decision so the fraud posture is auditable
-- (RBI model-governance expectation): WHAT was scored (payment_id, amount is implicit via payment),
-- the SCORE + VERDICT, the Explainable-AI top contributing features (top_reasons), the scoring MODEL
-- ('onnx' | 'rules' | 'none'), and a link to the ai.ai_decision row the §6.4 gateway wrote for the
-- same call (ai_decision_id). Written through FraudGatewayAuditor in its own transaction so recording
-- never blocks or rolls back a payment. Mirrors the V44 ai.ai_decision shape (RLS + append-only GRANTs).

CREATE SCHEMA IF NOT EXISTS fraud;

CREATE TABLE fraud.fraud_decision (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID        NOT NULL REFERENCES platform.merchants (id),
    payment_id     UUID,                   -- payment attempt reference (advisory; no FK, fraud may precede persist)
    score          INTEGER     NOT NULL CHECK (score >= 0 AND score <= 100),
    decision       TEXT        NOT NULL CHECK (decision IN ('ALLOW', 'CHALLENGE', 'BLOCK')),
    top_reasons    TEXT        NOT NULL DEFAULT '[]',  -- JSON-encoded SHAP-style top contributing features (§8.4)
    model          TEXT        NOT NULL,               -- scoring model: 'onnx' | 'rules' | 'none'
    ai_decision_id UUID,                   -- loose link to the ai.ai_decision audit row (§6.4 gateway)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fraud_decision_merchant ON fraud.fraud_decision (merchant_id, created_at DESC);
CREATE INDEX idx_fraud_decision_payment ON fraud.fraud_decision (merchant_id, payment_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE fraud.fraud_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE fraud.fraud_decision FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON fraud.fraud_decision
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: the fraud audit is append-only (SELECT + INSERT, never UPDATE/DELETE).
GRANT USAGE ON SCHEMA fraud TO qeet_pay_app;
GRANT SELECT, INSERT ON fraud.fraud_decision TO qeet_pay_app;
