-- V37 — Razorpay webhook event de-dup ledger (payments Module 01 / TAD §7.1). Razorpay delivers
-- webhooks at-least-once and redelivers on any non-2xx (or timeout), so the inbound handler records
-- every processed event id and short-circuits a replay to a 200 no-op — the money-movement and status
-- transitions an event drives are therefore applied EXACTLY once.
--
-- Keyed on the globally-unique Razorpay event id (the `x-razorpay-event-id` header; a SHA-256 hex of
-- the raw body when that header is absent). Because that id is GLOBAL — not per-tenant — and de-dup
-- must work independently of, and BEFORE, resolving which merchant an event belongs to (an event may
-- even be un-attributable), this table intentionally carries NO row-level security, exactly like the
-- public routing map paymentlinks.link_public_lookup (V36). It stores no tenant-private data:
-- merchant_id is recorded for audit/traceability only and is nullable.

CREATE TABLE payments.razorpay_webhook_events (
    event_id    TEXT        PRIMARY KEY,        -- x-razorpay-event-id, or sha256(raw body)
    event_type  TEXT        NOT NULL,           -- e.g. payment.captured, refund.processed
    merchant_id UUID,                           -- resolved tenant (audit only; null if unresolved)
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rzp_webhook_events_merchant ON payments.razorpay_webhook_events (merchant_id);

-- Intentionally NO row-level security: the event id is global and de-duplication must work before a
-- merchant is known (that is the whole point of an inbound provider webhook). No sensitive data here.
GRANT SELECT, INSERT ON payments.razorpay_webhook_events TO qeet_pay_app;
