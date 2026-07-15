-- V26 — Digital escrow (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). A buyer's payment is
-- held in escrow and released to the seller on delivery/milestone confirmation, or refunded to the
-- buyer if the condition fails. Partial releases/refunds are supported; every action is an
-- append-only escrow_event with the ledger entry that posted it.
--
-- Ledger postings (a dedicated escrow_payable liability account is opened on demand, ensureAccount):
--   hold    (buyer funds in):        debit settlement      / credit escrow_payable
--   release (to seller):             debit escrow_payable   / credit liability (seller payable)
--   refund  (to buyer):              debit escrow_payable   / credit settlement
-- escrow_payable nets to zero once fully allocated (Σreleases + Σrefunds == amount).
--
-- agreements mutate (released/refunded/status); events are append-only.

CREATE SCHEMA IF NOT EXISTS escrow;

-- An escrow hold between a buyer and a seller. released + refunded <= amount at all times.
CREATE TABLE escrow.escrow_agreements (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    buyer_ref        TEXT        NOT NULL,
    seller_ref       TEXT        NOT NULL,
    currency         TEXT        NOT NULL,
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor > 0),
    released_minor   BIGINT      NOT NULL DEFAULT 0 CHECK (released_minor >= 0),
    refunded_minor   BIGINT      NOT NULL DEFAULT 0 CHECK (refunded_minor >= 0),
    description      TEXT,
    status           TEXT        NOT NULL,   -- EscrowStatus (HELD | RELEASED | REFUNDED | SETTLED)
    hold_entry_id    UUID        NOT NULL,   -- the hold posting (ledger.journal_entries.id)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    CHECK (released_minor + refunded_minor <= amount_minor)
);
CREATE INDEX idx_escrow_agreements_merchant ON escrow.escrow_agreements (merchant_id);

-- Append-only audit of every hold / release / refund on an agreement.
CREATE TABLE escrow.escrow_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    agreement_id    UUID        NOT NULL REFERENCES escrow.escrow_agreements (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    type            TEXT        NOT NULL,   -- EscrowEventType (HOLD | RELEASE | REFUND)
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    ledger_entry_id UUID        NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_escrow_events_agreement ON escrow.escrow_events (agreement_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE escrow.escrow_agreements ENABLE ROW LEVEL SECURITY;
ALTER TABLE escrow.escrow_agreements FORCE  ROW LEVEL SECURITY;
ALTER TABLE escrow.escrow_events     ENABLE ROW LEVEL SECURITY;
ALTER TABLE escrow.escrow_events     FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON escrow.escrow_agreements
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON escrow.escrow_events
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: agreements mutate; events are append-only.
GRANT USAGE ON SCHEMA escrow TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON escrow.escrow_agreements TO qeet_pay_app;
GRANT SELECT, INSERT         ON escrow.escrow_events      TO qeet_pay_app;
