-- V51 — ONDC payment layer (PRD Module 13.4, Phase 3). A platform merchant collects a buyer-app
-- order over the Open Network for Digital Commerce on behalf of one or more network parties (a seller,
-- plus arbitrary additional legs such as a logistics partner — the split is not fixed at three
-- parties, per PRD 13.4). The collected gross is held escrow-like on order creation and, only after
-- fulfilment, settled per party: the operator keeps a commission (+ GST on it) and deducts statutory
-- TCS (CGST Act §52), leaving each party's net payable.
--
-- Ledger flow (settlement already holds the collected gross from payment capture):
--   create (hold)    debit  settlement   Σgross   / credit ondc_hold Σgross
--   settle (release) debit  ondc_hold     Σgross   / credit revenue Σcommission
--                                                   + tax_payable (Σcommission_gst + Σtcs)
--                                                   + liability   Σparty_net
-- Balanced because party_net = gross − commission − commission_gst − tcs per line. Cancelling posts
-- the exact offsetting entry (append-only corrections, per CLAUDE.md), never an UPDATE.
--
-- orders mutate (status / settle_entry_id / reversal_entry_id); settlement_lines are append-only.

CREATE SCHEMA IF NOT EXISTS ondc;

-- An ONDC network order. gross_minor = Σ settlement_lines.gross_minor.
CREATE TABLE ondc.orders (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id            UUID        NOT NULL REFERENCES platform.merchants (id),
    network_order_id       TEXT        NOT NULL,                 -- ONDC network transaction/order id
    buyer_app              TEXT        NOT NULL,                 -- ONDC buyer-app id
    seller_app             TEXT        NOT NULL,                 -- ONDC seller-app id
    currency               TEXT        NOT NULL,
    gross_minor            BIGINT      NOT NULL CHECK (gross_minor > 0),
    commission_minor       BIGINT      NOT NULL CHECK (commission_minor >= 0),
    commission_gst_minor   BIGINT      NOT NULL CHECK (commission_gst_minor >= 0),
    tcs_minor              BIGINT      NOT NULL CHECK (tcs_minor >= 0),
    party_net_minor        BIGINT      NOT NULL CHECK (party_net_minor >= 0),
    party_count            INT         NOT NULL,
    status                 TEXT        NOT NULL,                 -- OndcOrderStatus (CREATED|FULFILLED|SETTLED|CANCELLED)
    hold_entry_id          UUID        NOT NULL,                 -- the hold posting (ledger.journal_entries.id)
    settle_entry_id        UUID,                                 -- the release posting, set on settle
    reversal_entry_id      UUID,                                 -- the offsetting entry, set on cancel
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    fulfilled_at           TIMESTAMPTZ,
    settled_at             TIMESTAMPTZ,
    cancelled_at           TIMESTAMPTZ
);
CREATE INDEX idx_ondc_orders_merchant ON ondc.orders (merchant_id);
CREATE INDEX idx_ondc_orders_network  ON ondc.orders (merchant_id, network_order_id);

-- One party's slice of an order, with the full commission/GST/TCS breakdown retained for GSTR-8 audit.
CREATE TABLE ondc.settlement_lines (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID        NOT NULL REFERENCES ondc.orders (id),
    merchant_id           UUID        NOT NULL REFERENCES platform.merchants (id),
    party_ref             TEXT        NOT NULL,
    party_role            TEXT        NOT NULL,                  -- PartyRole (SELLER|LOGISTICS|PLATFORM|OTHER)
    gross_minor           BIGINT      NOT NULL CHECK (gross_minor > 0),
    commission_bps        INT         NOT NULL,
    commission_minor      BIGINT      NOT NULL CHECK (commission_minor >= 0),
    commission_gst_rate   INT         NOT NULL,
    commission_gst_minor  BIGINT      NOT NULL CHECK (commission_gst_minor >= 0),
    tcs_bps               INT         NOT NULL,
    tcs_minor             BIGINT      NOT NULL CHECK (tcs_minor >= 0),
    net_minor             BIGINT      NOT NULL CHECK (net_minor >= 0),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ondc_settlement_lines_order    ON ondc.settlement_lines (order_id);
CREATE INDEX idx_ondc_settlement_lines_merchant ON ondc.settlement_lines (merchant_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE ondc.orders           ENABLE ROW LEVEL SECURITY;
ALTER TABLE ondc.orders           FORCE  ROW LEVEL SECURITY;
ALTER TABLE ondc.settlement_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE ondc.settlement_lines FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON ondc.orders
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON ondc.settlement_lines
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: orders mutate (status transitions); settlement_lines are append-only.
GRANT USAGE ON SCHEMA ondc TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON ondc.orders           TO qeet_pay_app;
GRANT SELECT, INSERT         ON ondc.settlement_lines TO qeet_pay_app;
