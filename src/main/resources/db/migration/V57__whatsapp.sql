-- V57 — WhatsApp-native collection + subscription bot (PRD Module 09.2 "WhatsApp Pay" and 09.3
-- "WhatsApp subscription bot", TAD §5, Phase 2). Extends the existing messaging schema (V28) with the
-- inbound side of WhatsApp: messages received from customers, per-conversation session state for the
-- command bot, and in-chat Pay collections. A collection posts the canonical money-in ledger entry
-- (debit settlement / credit revenue) on confirm, identical to a virtual-account credit (V25).
--
-- inbound_messages are append-only (idempotent on the provider message id); sessions mutate
-- (state/last_command/message_count); pay_collections mutate (status CREATED -> PAID/FAILED).

CREATE SCHEMA IF NOT EXISTS messaging;

-- An inbound WhatsApp message from a customer. Idempotent per merchant on the Meta/WhatsApp message id.
CREATE TABLE messaging.inbound_messages (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    provider_message_id TEXT        NOT NULL,   -- Meta/WhatsApp wamid; unique per merchant (idempotency)
    wa_from             TEXT        NOT NULL,   -- sender phone / wa_id
    body                TEXT,                   -- raw message text
    parsed_command      TEXT,                   -- BotCommand (PAUSE|CANCEL|INVOICE|PLAN|USAGE|BALANCE|PAY|UNKNOWN)
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, provider_message_id)
);
CREATE INDEX idx_inbound_messages_merchant ON messaging.inbound_messages (merchant_id);

-- Per-(merchant, phone) conversational state for the WhatsApp bot. One row per customer phone.
CREATE TABLE messaging.whatsapp_sessions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    wa_phone      TEXT        NOT NULL,
    state         TEXT        NOT NULL DEFAULT 'IDLE',  -- WhatsAppSessionState (IDLE | ACTIVE | AWAITING_PAYMENT)
    last_command  TEXT,
    context_ref   TEXT,                                 -- subscription / pay-collection the chat is about
    message_count INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, wa_phone)
);
CREATE INDEX idx_whatsapp_sessions_merchant ON messaging.whatsapp_sessions (merchant_id);

-- An in-chat WhatsApp Pay collection. On confirm it posts the money-in entry recorded in ledger_entry_id.
CREATE TABLE messaging.whatsapp_pay_collections (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    payer_phone     TEXT,
    payer_vpa       TEXT,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),  -- paise
    currency        TEXT        NOT NULL,
    status          TEXT        NOT NULL,   -- WhatsAppPayStatus (CREATED | PAID | FAILED)
    description     TEXT,
    provider_ref    TEXT,                   -- sandbox rail reference, set on confirm
    ledger_entry_id UUID,                   -- the money-in posting (ledger.journal_entries.id), set on PAID
    related_ref     TEXT,                   -- originating order / invoice id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ
);
CREATE INDEX idx_whatsapp_pay_merchant ON messaging.whatsapp_pay_collections (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE messaging.inbound_messages         ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaging.inbound_messages         FORCE  ROW LEVEL SECURITY;
ALTER TABLE messaging.whatsapp_sessions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaging.whatsapp_sessions        FORCE  ROW LEVEL SECURITY;
ALTER TABLE messaging.whatsapp_pay_collections ENABLE ROW LEVEL SECURITY;
ALTER TABLE messaging.whatsapp_pay_collections FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON messaging.inbound_messages
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON messaging.whatsapp_sessions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON messaging.whatsapp_pay_collections
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: inbound is append-only; sessions + collections mutate.
GRANT USAGE ON SCHEMA messaging TO qeet_pay_app;
GRANT SELECT, INSERT         ON messaging.inbound_messages         TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON messaging.whatsapp_sessions        TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON messaging.whatsapp_pay_collections TO qeet_pay_app;
