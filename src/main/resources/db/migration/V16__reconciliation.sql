-- V16 — Reconciliation & Settlements (TAD §6.2, §7.1). The financial-integrity backbone that
-- turns the append-only ledger into a *provable* one: ingest a provider settlement report, record
-- the money movement in the ledger (debit bank + fees / credit the settlement holding account),
-- then reconcile every reported line against the captured payments and flag discrepancies for
-- human review (§6.2 "discrepancies flagged"). A nodal check asserts the holding account never
-- goes negative (we never settle out more than we hold).
--
-- settlements mutate (RECEIVED -> RECONCILED | DISCREPANCY) so the app role gets UPDATE; the
-- per-line items, reconciliation runs, and discrepancies are append-only (SELECT/INSERT only).

CREATE SCHEMA IF NOT EXISTS reconciliation;

-- A provider settlement batch: the PA (Razorpay/Cashfree/…) paid net funds to the merchant's
-- nodal/bank account. gross = fee + tax + net. Idempotent by (merchant, provider, provider id).
CREATE TABLE reconciliation.settlements (
    id                     UUID        PRIMARY KEY,
    merchant_id            UUID        NOT NULL REFERENCES platform.merchants (id),
    provider               TEXT        NOT NULL,
    provider_settlement_id TEXT        NOT NULL,   -- the PA's UTR / settlement reference
    currency               TEXT        NOT NULL,
    gross_amount_minor     BIGINT      NOT NULL CHECK (gross_amount_minor > 0),
    fee_amount_minor       BIGINT      NOT NULL CHECK (fee_amount_minor  >= 0),
    tax_amount_minor       BIGINT      NOT NULL CHECK (tax_amount_minor  >= 0),
    net_amount_minor       BIGINT      NOT NULL CHECK (net_amount_minor  >= 0),
    reported_net_minor     BIGINT,                 -- optional control total from the report
    item_count             INT         NOT NULL,
    status                 TEXT        NOT NULL,   -- SettlementStatus (RECEIVED, RECONCILED, …)
    ledger_entry_id        UUID,                   -- the settlement posting (ledger.journal_entries.id)
    settled_at             TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, provider, provider_settlement_id)
);
CREATE INDEX idx_settlements_merchant ON reconciliation.settlements (merchant_id);

-- One line of a settlement report: which payment settled, for how much, minus the PA's cut.
CREATE TABLE reconciliation.settlement_items (
    id                  UUID        PRIMARY KEY,
    settlement_id       UUID        NOT NULL REFERENCES reconciliation.settlements (id),
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    payment_id          UUID,                       -- our payment (payments.payments.id); null if unmatched
    provider_payment_id TEXT,                        -- the PA's payment reference, for audit
    gross_minor         BIGINT      NOT NULL CHECK (gross_minor > 0),
    fee_minor           BIGINT      NOT NULL CHECK (fee_minor >= 0),
    tax_minor           BIGINT      NOT NULL CHECK (tax_minor >= 0),
    net_minor           BIGINT      NOT NULL CHECK (net_minor >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_settlement_items_settlement ON reconciliation.settlement_items (settlement_id);
CREATE INDEX idx_settlement_items_payment    ON reconciliation.settlement_items (merchant_id, payment_id);

-- One reconciliation run over a settlement (1:1 in Phase 1). MATCHED, or DISCREPANCY with lines.
CREATE TABLE reconciliation.reconciliations (
    id                UUID        PRIMARY KEY,
    merchant_id       UUID        NOT NULL REFERENCES platform.merchants (id),
    settlement_id     UUID        NOT NULL REFERENCES reconciliation.settlements (id),
    status            TEXT        NOT NULL,   -- ReconciliationStatus (MATCHED, DISCREPANCY)
    matched_count     INT         NOT NULL,
    discrepancy_count INT         NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reconciliations_settlement ON reconciliation.reconciliations (settlement_id);

-- A single flagged mismatch (missing payment, amount/status mismatch, duplicate, batch or nodal
-- imbalance). expected = our ledger view; reported = what the settlement report said.
CREATE TABLE reconciliation.reconciliation_discrepancies (
    id                  UUID        PRIMARY KEY,
    reconciliation_id   UUID        NOT NULL REFERENCES reconciliation.reconciliations (id),
    merchant_id         UUID        NOT NULL REFERENCES platform.merchants (id),
    type                TEXT        NOT NULL,   -- DiscrepancyType
    payment_id          UUID,
    provider_payment_id TEXT,
    expected_minor      BIGINT,
    reported_minor      BIGINT,
    detail              TEXT        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_discrepancies_reconciliation
    ON reconciliation.reconciliation_discrepancies (reconciliation_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE reconciliation.settlements                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE reconciliation.settlements                   FORCE  ROW LEVEL SECURITY;
ALTER TABLE reconciliation.settlement_items              ENABLE ROW LEVEL SECURITY;
ALTER TABLE reconciliation.settlement_items              FORCE  ROW LEVEL SECURITY;
ALTER TABLE reconciliation.reconciliations               ENABLE ROW LEVEL SECURITY;
ALTER TABLE reconciliation.reconciliations               FORCE  ROW LEVEL SECURITY;
ALTER TABLE reconciliation.reconciliation_discrepancies  ENABLE ROW LEVEL SECURITY;
ALTER TABLE reconciliation.reconciliation_discrepancies  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON reconciliation.settlements
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON reconciliation.settlement_items
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON reconciliation.reconciliations
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON reconciliation.reconciliation_discrepancies
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: settlements mutate (status); the rest are append-only.
GRANT USAGE ON SCHEMA reconciliation TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON reconciliation.settlements TO qeet_pay_app;
GRANT SELECT, INSERT ON reconciliation.settlement_items             TO qeet_pay_app;
GRANT SELECT, INSERT ON reconciliation.reconciliations             TO qeet_pay_app;
GRANT SELECT, INSERT ON reconciliation.reconciliation_discrepancies TO qeet_pay_app;
