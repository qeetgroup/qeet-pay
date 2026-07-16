-- V35 — ESG / carbon-footprint tracking (PRD Module 16 "Sustainability", TAD §5, Phase 2/3). Estimates
-- the carbon footprint of each payment from its acceptance method and amount, and lets a merchant
-- purchase carbon offsets. Footprint estimates are informational and NEVER touch the ledger; buying an
-- offset costs money and posts a balanced entry (debit fees / credit settlement) for its cost.
--
-- Ledger postings (offset purchases only; recording a footprint posts nothing):
--   offset (cost > 0):  debit fees / credit settlement (the paid offset cost)
-- A zero-cost offset is still recorded but carries a null ledger_entry_id.
--
-- Both tables are append-only.

CREATE SCHEMA IF NOT EXISTS esg;

-- Append-only estimate of one payment's carbon footprint (informational — no ledger impact).
CREATE TABLE esg.carbon_records (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    transaction_ref  TEXT        NOT NULL,
    method           TEXT        NOT NULL,   -- CarbonMethod (UPI | CARD | NET_BANKING | WALLET)
    amount_minor     BIGINT      NOT NULL CHECK (amount_minor >= 0),
    grams_co2        BIGINT      NOT NULL CHECK (grams_co2 >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_carbon_records_merchant ON esg.carbon_records (merchant_id);

-- Append-only record of a carbon-offset purchase. ledger_entry_id is the posting that paid for it, or
-- null for a zero-cost offset (which never touches the ledger).
CREATE TABLE esg.carbon_offsets (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    grams_co2_offset BIGINT      NOT NULL CHECK (grams_co2_offset > 0),
    cost_minor       BIGINT      NOT NULL CHECK (cost_minor >= 0),
    currency         TEXT        NOT NULL,
    ledger_entry_id  UUID,                   -- null iff cost_minor = 0
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_carbon_offsets_merchant ON esg.carbon_offsets (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE esg.carbon_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.carbon_records FORCE  ROW LEVEL SECURITY;
ALTER TABLE esg.carbon_offsets ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.carbon_offsets FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON esg.carbon_records
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON esg.carbon_offsets
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both tables are append-only.
GRANT USAGE ON SCHEMA esg TO qeet_pay_app;
GRANT SELECT, INSERT ON esg.carbon_records TO qeet_pay_app;
GRANT SELECT, INSERT ON esg.carbon_offsets TO qeet_pay_app;
