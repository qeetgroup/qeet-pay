-- V19 — Marketplace split settlements (PRD Module 13, TAD §5 "Marketplace", Phase 2). A platform
-- merchant (the e-commerce operator) collects a buyer payment on behalf of one or more registered
-- sellers, then splits it: it keeps a commission (+ GST on that commission) and, as an e-commerce
-- operator, deducts TCS (CGST Act §52, 1% of the net value of supplies) and TDS (Income Tax
-- §194-O, 1% of gross) which it owes to the government. The remainder is the seller's payable.
--
-- Ledger posting for a split (settlement already holds the collected gross from payment capture):
--   debit settlement   Σgross
--   credit revenue      Σcommission
--   credit tax_payable (Σcommission_gst + Σtcs + Σtds)
--   credit liability    Σseller_net
-- Balanced because seller_net = gross − commission − commission_gst − tcs − tds per line. Cancelling
-- a split posts the exact offsetting entry (append-only corrections, per CLAUDE.md), never an UPDATE.
--
-- sellers mutate (status); splits mutate (status / reversal_entry_id); split_items are append-only.

CREATE SCHEMA IF NOT EXISTS marketplace;

-- A seller onboarded under a platform merchant. seller_ref is the operator's own id for the seller.
CREATE TABLE marketplace.sellers (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    seller_ref       TEXT        NOT NULL,
    name             TEXT        NOT NULL,
    gstin            TEXT,
    pan              TEXT,
    commission_bps   INT         NOT NULL DEFAULT 0 CHECK (commission_bps BETWEEN 0 AND 10000),
    status           TEXT        NOT NULL DEFAULT 'ACTIVE',  -- SellerStatus (ACTIVE | SUSPENDED)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, seller_ref)
);
CREATE INDEX idx_marketplace_sellers_merchant ON marketplace.sellers (merchant_id);

-- A split of one collected payment across sellers. gross_minor = Σ split_items.gross_minor.
CREATE TABLE marketplace.splits (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id            UUID        NOT NULL REFERENCES platform.merchants (id),
    payment_id             UUID,                       -- our payment (payments.payments.id); opaque here
    source_ref             TEXT,                       -- caller's external reference
    currency               TEXT        NOT NULL,
    gross_minor            BIGINT      NOT NULL CHECK (gross_minor > 0),
    commission_minor       BIGINT      NOT NULL CHECK (commission_minor >= 0),
    commission_gst_minor   BIGINT      NOT NULL CHECK (commission_gst_minor >= 0),
    tcs_minor              BIGINT      NOT NULL CHECK (tcs_minor >= 0),
    tds_minor              BIGINT      NOT NULL CHECK (tds_minor >= 0),
    seller_net_minor       BIGINT      NOT NULL CHECK (seller_net_minor >= 0),
    item_count             INT         NOT NULL,
    status                 TEXT        NOT NULL,        -- SplitStatus (POSTED | CANCELLED)
    ledger_entry_id        UUID        NOT NULL,        -- the split posting (ledger.journal_entries.id)
    reversal_entry_id      UUID,                        -- the offsetting entry, set on cancel
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at           TIMESTAMPTZ
);
CREATE INDEX idx_marketplace_splits_merchant ON marketplace.splits (merchant_id);
CREATE INDEX idx_marketplace_splits_payment  ON marketplace.splits (merchant_id, payment_id);

-- One seller's slice of a split, with the full tax breakdown retained for GSTR/settlement audit.
CREATE TABLE marketplace.split_items (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    split_id              UUID        NOT NULL REFERENCES marketplace.splits (id),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    seller_id             UUID        NOT NULL REFERENCES marketplace.sellers (id),
    seller_ref            TEXT        NOT NULL,
    gross_minor           BIGINT      NOT NULL CHECK (gross_minor > 0),
    commission_bps        INT         NOT NULL,
    commission_minor      BIGINT      NOT NULL CHECK (commission_minor >= 0),
    commission_gst_rate   INT         NOT NULL,
    commission_gst_minor  BIGINT      NOT NULL CHECK (commission_gst_minor >= 0),
    tcs_bps               INT         NOT NULL,
    tcs_minor             BIGINT      NOT NULL CHECK (tcs_minor >= 0),
    tds_bps               INT         NOT NULL,
    tds_minor             BIGINT      NOT NULL CHECK (tds_minor >= 0),
    net_minor             BIGINT      NOT NULL CHECK (net_minor >= 0),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_marketplace_split_items_split  ON marketplace.split_items (split_id);
CREATE INDEX idx_marketplace_split_items_seller ON marketplace.split_items (merchant_id, seller_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE marketplace.sellers     ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace.sellers     FORCE  ROW LEVEL SECURITY;
ALTER TABLE marketplace.splits      ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace.splits      FORCE  ROW LEVEL SECURITY;
ALTER TABLE marketplace.split_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE marketplace.split_items FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON marketplace.sellers
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON marketplace.splits
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON marketplace.split_items
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: sellers + splits mutate; items are append-only.
GRANT USAGE ON SCHEMA marketplace TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON marketplace.sellers     TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON marketplace.splits      TO qeet_pay_app;
GRANT SELECT, INSERT         ON marketplace.split_items TO qeet_pay_app;
