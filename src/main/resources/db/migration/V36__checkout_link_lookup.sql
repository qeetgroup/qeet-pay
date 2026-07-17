-- V36 — Public checkout link lookup (PRD Module 01 "links, pages, checkout surface", Phase 2). The
-- hosted-checkout surface lets a payer open a payment-link URL and pay it WITHOUT a merchant API key
-- (the link code is the capability, like a Stripe/Razorpay checkout URL). Payment-link codes are stored
-- UNIQUE(merchant_id, code) — NOT globally — so a PUBLIC "GET by code" needs a code → merchant/link
-- resolution path that works with NO merchant context.
--
-- This table is exactly that: a PUBLIC routing map (code → merchant/link only, NO sensitive data). It
-- carries no amount, title, status, payment id, reference or ledger info — just enough to resolve which
-- tenant a code belongs to so the app can then apply the merchant scope and read the RLS-protected
-- paymentlinks.payment_links row. Because it holds no tenant-private data and MUST be readable before a
-- merchant is known, it intentionally has NO row-level security.

CREATE TABLE paymentlinks.link_public_lookup (
    code        TEXT        PRIMARY KEY,                                  -- public share code from the link URL
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),  -- tenant the code resolves to
    link_id     UUID        NOT NULL,                                     -- the payment_links row id
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_link_public_lookup_merchant ON paymentlinks.link_public_lookup (merchant_id);

-- Intentionally NO row-level security: this is a public routing map that must be readable with no
-- merchant context (that is the whole point of the hosted-checkout path). It exposes no sensitive data.
GRANT SELECT, INSERT ON paymentlinks.link_public_lookup TO qeet_pay_app;

-- Backfill existing links so previously-created codes are resolvable via the public path.
INSERT INTO paymentlinks.link_public_lookup (code, merchant_id, link_id, created_at)
SELECT code, merchant_id, id, created_at FROM paymentlinks.payment_links;
