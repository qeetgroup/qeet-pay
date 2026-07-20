-- V44 — AI gateway substrate (PRD §6 "AI Feature Specifications", esp. §6.4 Human-Oversight & Safety
-- Matrix; TAD §8.2 AI Gateway / §8.3 AI safety controls). The single entry point through which every
-- future AI feature (fraud XAI, AI dunning, GST classification, NLQ, reconciliation copilot, tax
-- optimizer, lending/treasury copilots) must route. This migration persists the decision audit trail
-- the matrix requires: "Every AI suggestion + human decision logged to qeet-logs".
--
-- Every AiGateway call records exactly one append-only ai_decision row capturing WHAT was asked
-- (input_hash + masked_input — never raw PAN/Aadhaar/PII), WHAT the model/deterministic path returned
-- (output_json + confidence), and HOW the safety matrix resolved it (human_reviewed, fell_back). The
-- same call also enqueues a platform.outbox_event ("ai.decision.recorded") for the qeet-logs relay.

CREATE SCHEMA IF NOT EXISTS ai;

-- One row per AiGateway decision. Append-only: an audit trail is never mutated after the fact.
CREATE TABLE ai.ai_decision (
    id             UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID             NOT NULL REFERENCES platform.merchants (id),
    feature        TEXT             NOT NULL,   -- AiFeature key, e.g. "gst.classification" / "lending.decision"
    model          TEXT             NOT NULL,   -- the model id used (or the sandbox stand-in's id)
    input_hash     TEXT             NOT NULL,   -- SHA-256 of the raw input (traceable without storing raw PII)
    masked_input   TEXT             NOT NULL,   -- PII/PAN/Aadhaar/card/email/phone masked (§6.4 "no raw PII/PAN to the LLM")
    output_json    TEXT             NOT NULL,   -- the returned decision (model result OR deterministic fallback)
    confidence     DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    human_reviewed BOOLEAN          NOT NULL DEFAULT false,  -- §6.4 human-in-the-loop flag on money-affecting types
    fell_back      BOOLEAN          NOT NULL DEFAULT false,  -- deterministic path taken (stale/ambiguous/low-conf/error/unreviewed)
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_decision_merchant ON ai.ai_decision (merchant_id, created_at DESC);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE ai.ai_decision ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai.ai_decision FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON ai.ai_decision
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: the decision audit is append-only (SELECT + INSERT, never UPDATE/DELETE).
GRANT USAGE ON SCHEMA ai TO qeet_pay_app;
GRANT SELECT, INSERT ON ai.ai_decision TO qeet_pay_app;
