-- V30 — Buy-Now-Pay-Later / credit line on UPI (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3).
-- At checkout the BNPL provider funds the merchant the full order amount immediately (posted once:
-- debit settlement / credit revenue) while the customer repays the platform over N monthly
-- installments. Optional flat interest (interest_bps) is added to the order amount to form the total
-- payable, split into equal installments with the rounding remainder carried on the last one.
-- Installment repayments are customer <-> platform, so they do NOT touch the merchant ledger.
--
-- Ledger posting (one balanced entry per agreement, at creation):
--   funding (merchant paid upfront):  debit settlement / credit revenue
--
-- agreements mutate (paid_installments/status/settled_at); installments mutate (status/paid_at).

CREATE SCHEMA IF NOT EXISTS bnpl;

-- A BNPL agreement: the funded order plus its repayment plan.
CREATE TABLE bnpl.bnpl_agreements (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    customer_ref         TEXT        NOT NULL,
    order_ref            TEXT        NOT NULL,
    order_amount_minor   BIGINT      NOT NULL CHECK (order_amount_minor > 0),
    interest_bps         INTEGER     NOT NULL DEFAULT 0 CHECK (interest_bps >= 0),
    total_payable_minor  BIGINT      NOT NULL CHECK (total_payable_minor > 0),
    installments_count   INTEGER     NOT NULL CHECK (installments_count >= 1),
    paid_installments    INTEGER     NOT NULL DEFAULT 0 CHECK (paid_installments >= 0),
    currency             TEXT        NOT NULL,
    status               TEXT        NOT NULL,   -- BnplStatus (ACTIVE | SETTLED | CANCELLED)
    sale_entry_id        UUID        NOT NULL,   -- the funding posting (ledger.journal_entries.id)
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at           TIMESTAMPTZ,
    CHECK (paid_installments <= installments_count)
);
CREATE INDEX idx_bnpl_agreements_merchant ON bnpl.bnpl_agreements (merchant_id);

-- One scheduled installment of an agreement. seq is 0-based; amounts sum to total_payable_minor.
CREATE TABLE bnpl.bnpl_installments (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    agreement_id  UUID        NOT NULL REFERENCES bnpl.bnpl_agreements (id),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    seq           INTEGER     NOT NULL CHECK (seq >= 0),
    due_date      DATE        NOT NULL,
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    status        TEXT        NOT NULL,   -- InstallmentStatus (PENDING | PAID)
    paid_at       TIMESTAMPTZ
);
CREATE INDEX idx_bnpl_installments_merchant  ON bnpl.bnpl_installments (merchant_id);
CREATE INDEX idx_bnpl_installments_agreement ON bnpl.bnpl_installments (agreement_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE bnpl.bnpl_agreements   ENABLE ROW LEVEL SECURITY;
ALTER TABLE bnpl.bnpl_agreements   FORCE  ROW LEVEL SECURITY;
ALTER TABLE bnpl.bnpl_installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE bnpl.bnpl_installments FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON bnpl.bnpl_agreements
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON bnpl.bnpl_installments
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both tables mutate (agreement status/counters; installment status).
GRANT USAGE ON SCHEMA bnpl TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON bnpl.bnpl_agreements   TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON bnpl.bnpl_installments TO qeet_pay_app;
