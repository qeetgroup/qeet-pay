-- V32 — Embedded insurance (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). A merchant issues
-- payment-protection / fraud-cover / subscription-interruption policies against a payment; each premium
-- is collected up front into an on-demand insurance_reserve liability and claims are paid out of it.
-- Every payout is a balanced ledger entry recorded on the claim.
--
-- Ledger postings (a dedicated insurance_reserve liability account is opened on demand, ensureAccount):
--   issue policy (collect premium): debit settlement       / credit insurance_reserve
--   approve claim (pay out):        debit insurance_reserve / credit settlement
-- A rejected claim moves no money. insurance_reserve holds net premiums less payouts.
--
-- policies mutate (status / cancelled_at); claims mutate (status / payout / decided_at).

CREATE SCHEMA IF NOT EXISTS insurance;

-- A protection policy a merchant has issued to a payer. Premium collected up front into the reserve.
CREATE TABLE insurance.insurance_policies (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    product            TEXT        NOT NULL,   -- InsuranceProduct (PAYMENT_PROTECTION | FRAUD_COVER | SUBSCRIPTION_INTERRUPTION)
    holder_ref         TEXT        NOT NULL,
    premium_minor      BIGINT      NOT NULL CHECK (premium_minor > 0),
    cover_amount_minor BIGINT      NOT NULL CHECK (cover_amount_minor > 0),
    currency           TEXT        NOT NULL,
    status             TEXT        NOT NULL,   -- PolicyStatus (ACTIVE | CANCELLED | EXPIRED)
    premium_entry_id   UUID        NOT NULL,   -- the premium posting (ledger.journal_entries.id)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at       TIMESTAMPTZ
);
CREATE INDEX idx_insurance_policies_merchant ON insurance.insurance_policies (merchant_id);

-- A claim filed against a policy's cover, then paid out of the reserve or rejected.
CREATE TABLE insurance.insurance_claims (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id       UUID        NOT NULL REFERENCES insurance.insurance_policies (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    reason          TEXT,
    status          TEXT        NOT NULL,   -- ClaimStatus (FILED | APPROVED | PAID | REJECTED)
    payout_entry_id UUID,                   -- the payout posting, once paid (ledger.journal_entries.id)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ
);
CREATE INDEX idx_insurance_claims_policy    ON insurance.insurance_claims (policy_id);
CREATE INDEX idx_insurance_claims_merchant  ON insurance.insurance_claims (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE insurance.insurance_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE insurance.insurance_policies FORCE  ROW LEVEL SECURITY;
ALTER TABLE insurance.insurance_claims   ENABLE ROW LEVEL SECURITY;
ALTER TABLE insurance.insurance_claims   FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON insurance.insurance_policies
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON insurance.insurance_claims
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both policies and claims mutate (status transitions).
GRANT USAGE ON SCHEMA insurance TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON insurance.insurance_policies TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON insurance.insurance_claims   TO qeet_pay_app;
