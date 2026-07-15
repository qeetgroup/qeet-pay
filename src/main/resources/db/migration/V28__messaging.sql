-- V28 — WhatsApp-native messaging (PRD Module 09 "Messaging", TAD §5, Phase 2). Turns a domain intent
-- (deliver an invoice, send a dunning reminder, confirm a payout) into a rendered, templated dispatch
-- and emits it via the transactional outbox for qeet-notify to deliver over WhatsApp/SMS/email. The
-- outbox relay ships disabled by default (§9.5), so a dispatch stays QUEUED until the relay is on and
-- a delivery callback marks it SENT/FAILED — the same decoupling the webhooks module uses.
--
-- templates mutate (body/active); dispatches mutate (status/provider_ref).

CREATE SCHEMA IF NOT EXISTS messaging;

-- A merchant-configured message template with {{placeholder}} variables.
CREATE TABLE messaging.message_templates (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    template_key  TEXT        NOT NULL,   -- 'invoice' | 'dunning_reminder' | 'payout_confirmation' | …
    channel       TEXT        NOT NULL,   -- MessageChannel (WHATSAPP | SMS | EMAIL)
    body          TEXT        NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, template_key, channel)
);
CREATE INDEX idx_message_templates_merchant ON messaging.message_templates (merchant_id);

-- A rendered, queued/sent message. related_ref links it back to the invoice/subscription/payout.
CREATE TABLE messaging.message_dispatches (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID        NOT NULL REFERENCES platform.merchants (id),
    template_key   TEXT        NOT NULL,
    channel        TEXT        NOT NULL,
    recipient      TEXT        NOT NULL,
    rendered_body  TEXT        NOT NULL,
    status         TEXT        NOT NULL,   -- DispatchStatus (QUEUED | SENT | FAILED)
    provider_ref   TEXT,                   -- qeet-notify delivery id, set on callback
    related_ref    TEXT,                   -- originating invoice / subscription / payout id
    failure_reason TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);
CREATE INDEX idx_message_dispatches_merchant ON messaging.message_dispatches (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE messaging.message_templates  ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaging.message_templates  FORCE  ROW LEVEL SECURITY;
ALTER TABLE messaging.message_dispatches ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaging.message_dispatches FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON messaging.message_templates
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON messaging.message_dispatches
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both mutate.
GRANT USAGE ON SCHEMA messaging TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON messaging.message_templates  TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON messaging.message_dispatches TO qeet_pay_app;
