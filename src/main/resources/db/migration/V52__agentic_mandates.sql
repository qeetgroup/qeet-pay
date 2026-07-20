-- V52 — Agentic mandates + MCP tool manifest (PRD Module 17.5 "AI-agent mandates", Novel N1). An
-- AI agent acts on a merchant's behalf under a scoped, revocable *mandate* of authority: a per-agent
-- allowlist of operations + payees, a per-transaction cap and a cumulative spend cap, a validity
-- window, and a running spent counter. Authorization is a purely deterministic decision (active +
-- in-window + within caps + operation/payee allowlisted → ALLOW, else DENY with a reason); no money
-- moves here and the ledger is never touched — this is a governance layer that sits on top of the
-- deterministic payment core (like ai/). Every decision is an append-only agent_mandate_use.
--
-- Idempotency for authorize reuses platform.idempotency_keys (agent-supplied key, namespaced), so a
-- retried authorize returns the original decision and never double-spends.

CREATE SCHEMA IF NOT EXISTS agentic;

-- A scoped grant of authority to one AI agent. spent_minor <= cumulative_cap_minor at all times.
CREATE TABLE agentic.agent_mandates (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    agent_id              TEXT        NOT NULL,   -- the external agent identifier
    label                 TEXT,                   -- human-friendly description
    max_txn_minor         BIGINT      NOT NULL CHECK (max_txn_minor > 0),      -- per-transaction cap
    cumulative_cap_minor  BIGINT      NOT NULL CHECK (cumulative_cap_minor > 0), -- lifetime spend cap
    spent_minor           BIGINT      NOT NULL DEFAULT 0 CHECK (spent_minor >= 0),
    allowed_operations    TEXT        NOT NULL DEFAULT '[]',  -- JSON array of MCP tool names ([] = any)
    allowed_payees        TEXT        NOT NULL DEFAULT '[]',  -- JSON array of payee refs ([] = any)
    valid_from            TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at            TIMESTAMPTZ,            -- NULL = never expires
    status                TEXT        NOT NULL,   -- AgentMandateStatus (ACTIVE | REVOKED | EXPIRED)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at            TIMESTAMPTZ,
    CHECK (spent_minor <= cumulative_cap_minor)
);
CREATE INDEX idx_agent_mandates_merchant ON agentic.agent_mandates (merchant_id);

-- Append-only audit of every authorization decision made against a mandate (ALLOW or DENY).
CREATE TABLE agentic.agent_mandate_uses (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    mandate_id    UUID        NOT NULL REFERENCES agentic.agent_mandates (id),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    idem_key      TEXT,                   -- agent-supplied idempotency key (nullable)
    operation     TEXT        NOT NULL,   -- MCP tool name the agent tried to invoke
    payee_ref     TEXT,
    amount_minor  BIGINT      NOT NULL,
    allowed       BOOLEAN     NOT NULL,   -- the decision
    reason        TEXT        NOT NULL,   -- deterministic reason for the decision
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_mandate_uses_mandate ON agentic.agent_mandate_uses (mandate_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE agentic.agent_mandates     ENABLE ROW LEVEL SECURITY;
ALTER TABLE agentic.agent_mandates     FORCE  ROW LEVEL SECURITY;
ALTER TABLE agentic.agent_mandate_uses ENABLE ROW LEVEL SECURITY;
ALTER TABLE agentic.agent_mandate_uses FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON agentic.agent_mandates
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON agentic.agent_mandate_uses
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: mandates mutate (spent/status); uses are append-only.
GRANT USAGE ON SCHEMA agentic TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON agentic.agent_mandates     TO qeet_pay_app;
GRANT SELECT, INSERT         ON agentic.agent_mandate_uses TO qeet_pay_app;
