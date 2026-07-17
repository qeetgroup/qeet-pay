-- V29 — Payment links (PRD Module 01 "links, pages, checkout surface", Phase 2). A merchant creates a
-- shareable link for a fixed or customer-entered amount; paying it drives a real payment through the
-- payments module (create → authorize → capture), which posts the money-in ledger entry. The link
-- tracks its own lifecycle and records the resulting payment id.
--
-- links mutate (status ACTIVE -> PAID/EXPIRED/CANCELLED, payment_id).

CREATE SCHEMA IF NOT EXISTS paymentlinks;

CREATE TABLE paymentlinks.payment_links (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    code          TEXT        NOT NULL,   -- short slug used in the public link URL
    title         TEXT        NOT NULL,
    amount_minor  BIGINT,                 -- NULL = customer enters the amount at pay time
    currency      TEXT        NOT NULL,
    reference     TEXT,                    -- merchant's order/reference id
    status        TEXT        NOT NULL,   -- PaymentLinkStatus (ACTIVE | PAID | EXPIRED | CANCELLED)
    payment_id    UUID,                    -- the payment created when the link was paid
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at       TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    CHECK (amount_minor IS NULL OR amount_minor > 0),
    UNIQUE (merchant_id, code)
);
CREATE INDEX idx_payment_links_merchant ON paymentlinks.payment_links (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE paymentlinks.payment_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE paymentlinks.payment_links FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON paymentlinks.payment_links
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT USAGE ON SCHEMA paymentlinks TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON paymentlinks.payment_links TO qeet_pay_app;
