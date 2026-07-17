-- V24 — Embedded lending (PRD Module 10, TAD §5 "Embedded Finance", Phase 2). AA-powered (Account
-- Aggregator) working-capital advances underwritten from a merchant's settlement/GST history and
-- repaid as a fixed % of daily settlement (revenue-based financing). An offer is underwritten, the
-- merchant accepts, funds are disbursed, and repayments are swept from settlements until the total
-- repayable (principal + factor fee) is cleared.
--
-- Ledger postings (a dedicated loan_payable liability account is opened on demand, ensureAccount):
--   disbursement: debit settlement (principal) + debit fees (finance fee) / credit loan_payable (total)
--   repayment:    debit loan_payable (swept) / credit settlement (swept)
-- So loan_payable nets to zero when the advance is repaid; the fee is expensed at origination.
--
-- offers mutate (status); loans mutate (outstanding/status); repayments are append-only.

CREATE SCHEMA IF NOT EXISTS lending;

-- An underwriting decision: terms offered to the merchant, valid until expires_at.
CREATE TABLE lending.loan_offers (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id                 UUID        NOT NULL REFERENCES platform.merchants (id),
    currency                    TEXT        NOT NULL,
    principal_minor             BIGINT      NOT NULL CHECK (principal_minor > 0),
    fee_bps                     INT         NOT NULL CHECK (fee_bps BETWEEN 0 AND 10000),
    fee_minor                   BIGINT      NOT NULL CHECK (fee_minor >= 0),
    total_repayable_minor       BIGINT      NOT NULL CHECK (total_repayable_minor > 0),
    repayment_percent_bps       INT         NOT NULL CHECK (repayment_percent_bps BETWEEN 1 AND 10000),
    basis_monthly_volume_minor  BIGINT      NOT NULL CHECK (basis_monthly_volume_minor >= 0),
    status                      TEXT        NOT NULL,   -- LoanOfferStatus (OFFERED | ACCEPTED | EXPIRED | DECLINED)
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_offers_merchant ON lending.loan_offers (merchant_id);

-- An accepted + disbursed advance. outstanding_minor decreases with each repayment until REPAID.
CREATE TABLE lending.loans (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id            UUID        NOT NULL REFERENCES platform.merchants (id),
    offer_id               UUID        NOT NULL REFERENCES lending.loan_offers (id),
    currency               TEXT        NOT NULL,
    principal_minor        BIGINT      NOT NULL CHECK (principal_minor > 0),
    fee_minor              BIGINT      NOT NULL CHECK (fee_minor >= 0),
    total_repayable_minor  BIGINT      NOT NULL CHECK (total_repayable_minor > 0),
    outstanding_minor      BIGINT      NOT NULL CHECK (outstanding_minor >= 0),
    repayment_percent_bps  INT         NOT NULL CHECK (repayment_percent_bps BETWEEN 1 AND 10000),
    status                 TEXT        NOT NULL,   -- LoanStatus (ACTIVE | REPAID | WRITTEN_OFF)
    disbursed_entry_id     UUID        NOT NULL,   -- the disbursement posting (ledger.journal_entries.id)
    disbursed_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    repaid_at              TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_loans_merchant ON lending.loans (merchant_id);
CREATE INDEX idx_loans_offer    ON lending.loans (offer_id);

-- One repayment sweep from a settlement. swept_minor = the amount applied to the loan.
CREATE TABLE lending.loan_repayments (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id                 UUID        NOT NULL REFERENCES lending.loans (id),
    merchant_id             UUID        NOT NULL REFERENCES platform.merchants (id),
    settlement_amount_minor BIGINT      NOT NULL CHECK (settlement_amount_minor >= 0),
    swept_minor             BIGINT      NOT NULL CHECK (swept_minor > 0),
    ledger_entry_id         UUID        NOT NULL,   -- the repayment posting
    source_ref              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_loan_repayments_loan ON lending.loan_repayments (loan_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE lending.loan_offers     ENABLE ROW LEVEL SECURITY;
ALTER TABLE lending.loan_offers     FORCE  ROW LEVEL SECURITY;
ALTER TABLE lending.loans           ENABLE ROW LEVEL SECURITY;
ALTER TABLE lending.loans           FORCE  ROW LEVEL SECURITY;
ALTER TABLE lending.loan_repayments ENABLE ROW LEVEL SECURITY;
ALTER TABLE lending.loan_repayments FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON lending.loan_offers
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON lending.loans
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON lending.loan_repayments
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: offers + loans mutate; repayments are append-only.
GRANT USAGE ON SCHEMA lending TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON lending.loan_offers     TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON lending.loans           TO qeet_pay_app;
GRANT SELECT, INSERT         ON lending.loan_repayments TO qeet_pay_app;
