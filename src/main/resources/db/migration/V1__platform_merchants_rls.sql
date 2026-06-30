-- V1 — Platform foundation: the merchant (tenant) registry, API keys, idempotency, and the
-- transactional outbox. The multi-tenant RLS backbone keyed on the per-request GUC
-- `app.current_merchant_id` (TAD §6.1) is applied to merchant-owned tables in V2 (the ledger).
--
-- platform.merchants is the tenant registry itself, so it is NOT RLS-scoped. api_keys is the
-- pre-auth lookup table (resolves a key -> merchant), also not RLS-scoped; both are addressed by
-- unique keys. idempotency_keys / outbox_event carry merchant_id and are scoped explicitly in
-- queries.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.merchants (
    id         UUID        PRIMARY KEY,
    slug       TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    status     TEXT        NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform.api_keys (
    id          UUID        PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    key_hash    TEXT        NOT NULL UNIQUE,   -- SHA-256 hex of the raw qp_… secret
    key_prefix  TEXT        NOT NULL,          -- first chars, for display
    scopes      TEXT        NOT NULL,          -- space-separated pay:* scopes
    status      TEXT        NOT NULL DEFAULT 'active',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_merchant ON platform.api_keys (merchant_id);

CREATE TABLE platform.idempotency_keys (
    id              UUID        PRIMARY KEY,
    merchant_id     UUID        NOT NULL,
    idem_key        TEXT        NOT NULL,
    response_status INT         NOT NULL,
    response_body   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, idem_key)
);

CREATE TABLE platform.outbox_event (
    id           UUID        PRIMARY KEY,
    merchant_id  UUID        NOT NULL,
    subject      TEXT        NOT NULL,         -- pay.{merchant_id}.events.{type}
    event_type   TEXT        NOT NULL,
    payload      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON platform.outbox_event (created_at) WHERE published_at IS NULL;
