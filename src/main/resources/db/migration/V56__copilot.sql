-- V56 — LLM copilots (PRD Module 12.5 Treasury & Cash-Flow Copilot, N7 Reconciliation Copilot, and
-- Module 17 Natural-Language Query). A merchant-facing conversational surface that answers cash-flow /
-- settlement / working-capital, reconciliation-break/leakage, and plain-English metric questions by
-- reading the merchant's own analytics + reconciliation figures. Every answer is produced through the
-- AI gateway (PRD §6.4 — PII masked, human-in-loop, deterministic fallback, audited to ai.ai_decision)
-- and always cites the underlying figures; the conversation trail is persisted here for audit.
--
-- This module never moves money and holds no ledger postings — it is a read-only + conversational
-- surface on top of the deterministic core. Two tables:
--   copilot_conversations — one thread per (merchant, surface); title + timestamps, mutable updated_at.
--   copilot_messages      — append-only USER / ASSISTANT turns; assistant turns carry the cited
--                           figures (figures_json), the gateway confidence + fell_back flag, and a
--                           link (ai_decision_id) into the ai.ai_decision audit row the gateway wrote.

CREATE SCHEMA IF NOT EXISTS copilot;

-- A conversation thread bound to one copilot surface (TREASURY | RECONCILIATION | QUERY).
CREATE TABLE copilot.copilot_conversations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id  UUID        NOT NULL REFERENCES platform.merchants (id),
    surface      TEXT        NOT NULL,   -- CopilotSurface: TREASURY | RECONCILIATION | QUERY
    title        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_copilot_conversations_merchant ON copilot.copilot_conversations (merchant_id, updated_at DESC);

-- Append-only turns. USER rows hold the raw question; ASSISTANT rows hold the answer narrative plus the
-- cited figures, the gateway confidence/fell_back, and the ai.ai_decision link. Never mutated.
CREATE TABLE copilot.copilot_messages (
    id              UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID             NOT NULL REFERENCES copilot.copilot_conversations (id),
    merchant_id     UUID             NOT NULL REFERENCES platform.merchants (id),
    role            TEXT             NOT NULL,   -- CopilotRole: USER | ASSISTANT
    content         TEXT             NOT NULL,   -- the question (USER) or the answer narrative (ASSISTANT)
    figures_json    TEXT,                        -- cited underlying figures (ASSISTANT turns)
    confidence      DOUBLE PRECISION,            -- gateway confidence in the answer (ASSISTANT turns)
    fell_back       BOOLEAN,                     -- deterministic summary was used (ASSISTANT turns)
    ai_decision_id  UUID,                        -- link into ai.ai_decision (the gateway audit row)
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_copilot_messages_conversation ON copilot.copilot_messages (conversation_id, created_at);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE copilot.copilot_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE copilot.copilot_conversations FORCE  ROW LEVEL SECURITY;
ALTER TABLE copilot.copilot_messages      ENABLE ROW LEVEL SECURITY;
ALTER TABLE copilot.copilot_messages      FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON copilot.copilot_conversations
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON copilot.copilot_messages
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: conversations mutate (updated_at); messages are append-only.
GRANT USAGE ON SCHEMA copilot TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON copilot.copilot_conversations TO qeet_pay_app;
GRANT SELECT, INSERT         ON copilot.copilot_messages      TO qeet_pay_app;
