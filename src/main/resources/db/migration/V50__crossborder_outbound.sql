-- V50 — Outbound / import cross-border remittances (PRD Module 14.4, TAD §5 "Cross-Border", Phase 3
-- / PA-CB-I). An Indian merchant pays a foreign vendor/SaaS/cloud in a foreign currency via SWIFT
-- (with a FEMA/RBI purpose code). The foreign amount is converted to INR at the captured FX rate; the
-- LRS (Liberalised Remittance Scheme) financial-year running total is tracked per merchant, and 2.5%
-- TCS is collected on the cumulative amount above the LRS threshold. Creating a remittance posts a
-- balanced money-out entry (debit settlement / credit bank / credit tax_payable); a failed wire posts
-- the exact offsetting entry.
--
-- Reuses the existing `crossborder` schema (V27). outbound_remittances mutate (status
-- CREATED -> REMITTED | FAILED); events are append-only.

CREATE SCHEMA IF NOT EXISTS crossborder;

-- One outbound (import) remittance to a foreign beneficiary via SWIFT.
CREATE TABLE crossborder.outbound_remittances (
    id                           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id                  UUID          NOT NULL REFERENCES platform.merchants (id),
    beneficiary_name             TEXT          NOT NULL,
    beneficiary_swift            TEXT          NOT NULL,   -- beneficiary bank SWIFT/BIC
    beneficiary_account          TEXT          NOT NULL,   -- IBAN / account number
    beneficiary_country          TEXT          NOT NULL,   -- ISO country code
    purpose_code                 TEXT          NOT NULL,   -- FEMA/RBI purpose code (e.g. S0801)
    currency                     TEXT          NOT NULL,   -- foreign currency (USD, EUR, …)
    foreign_amount_minor         BIGINT        NOT NULL CHECK (foreign_amount_minor > 0),
    fx_rate                      NUMERIC(18,6) NOT NULL CHECK (fx_rate > 0),  -- INR per 1 unit of foreign currency
    principal_inr_minor          BIGINT        NOT NULL CHECK (principal_inr_minor > 0),  -- INR wired to vendor
    tcs_minor                    BIGINT        NOT NULL CHECK (tcs_minor >= 0),           -- 2.5% TCS above LRS threshold
    inr_debited_minor            BIGINT        NOT NULL CHECK (inr_debited_minor > 0),    -- principal + TCS
    financial_year               TEXT          NOT NULL,   -- Indian FY label (e.g. 2026-27)
    lrs_cumulative_before_minor  BIGINT        NOT NULL CHECK (lrs_cumulative_before_minor >= 0),
    lrs_cumulative_after_minor   BIGINT        NOT NULL CHECK (lrs_cumulative_after_minor >= 0),
    status                       TEXT          NOT NULL,   -- OutboundRemittanceStatus (CREATED | REMITTED | FAILED)
    ledger_entry_id              UUID          NOT NULL,   -- the money-out posting (ledger.journal_entries.id)
    reversal_entry_id            UUID,                     -- the offsetting posting when FAILED
    remittance_reference         TEXT,                     -- SWIFT MT103 / UTR captured on settlement
    failure_reason               TEXT,
    remitted_at                  TIMESTAMPTZ,
    failed_at                    TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbound_remittances_merchant ON crossborder.outbound_remittances (merchant_id);
CREATE INDEX idx_outbound_remittances_merchant_fy
    ON crossborder.outbound_remittances (merchant_id, financial_year);

-- Append-only transitions (created / remitted / failed) on an outbound remittance.
CREATE TABLE crossborder.outbound_remittance_events (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    remittance_id    UUID        NOT NULL REFERENCES crossborder.outbound_remittances (id),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    type             TEXT        NOT NULL,   -- OutboundRemittanceEventType (CREATED | REMITTED | FAILED)
    amount_minor     BIGINT      NOT NULL,
    ledger_entry_id  UUID,                   -- posting for this event, when one exists
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbound_remittance_events_remittance
    ON crossborder.outbound_remittance_events (remittance_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE crossborder.outbound_remittances        ENABLE ROW LEVEL SECURITY;
ALTER TABLE crossborder.outbound_remittances        FORCE  ROW LEVEL SECURITY;
ALTER TABLE crossborder.outbound_remittance_events  ENABLE ROW LEVEL SECURITY;
ALTER TABLE crossborder.outbound_remittance_events  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON crossborder.outbound_remittances
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON crossborder.outbound_remittance_events
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: remittances mutate (status); events are append-only.
GRANT USAGE ON SCHEMA crossborder TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON crossborder.outbound_remittances       TO qeet_pay_app;
GRANT SELECT, INSERT         ON crossborder.outbound_remittance_events TO qeet_pay_app;
