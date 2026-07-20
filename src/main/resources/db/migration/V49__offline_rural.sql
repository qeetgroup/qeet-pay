-- V49 — Offline & Rural payments (PRD Module 15). Four India-first "reach" rails, all simulated
-- (sandbox), consistent with the rest of Qeet Pay:
--   * Bharat QR      — a generated unified QR (UPI + RuPay + Visa + Mastercard). Generation is not a
--                      payment, so it never posts to the ledger. Append-only.
--   * UPI Lite       — an on-device low-value wallet (balance_minor). A top-up posts money-in
--                      (debit settlement / credit liability); a spend posts (debit liability /
--                      credit revenue). Per-txn ≤ ₹500 and per-day ≤ ₹2,000 enforced in the service.
--   * UPI 123Pay     — a feature-phone / IVR intent (CREATED -> CONFIRMED); confirming posts money-in.
--   * POS/Tap-to-Pay — an in-person capture on a registered device; posts money-in.
--
-- Wallets / intents / devices mutate (status, balance); QRs, wallet txns and POS txns are append-only.

CREATE SCHEMA IF NOT EXISTS offline;

-- Bharat QR: a generated unified QR payload for a merchant (dynamic when amount_minor is set).
CREATE TABLE offline.bharat_qrs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    merchant_name TEXT        NOT NULL,
    amount_minor  BIGINT      CHECK (amount_minor IS NULL OR amount_minor > 0),  -- NULL = static/open
    currency      TEXT        NOT NULL,
    reference     TEXT        NOT NULL,
    payload       TEXT        NOT NULL,   -- EMVCo-style unified payload (UPI intent + card networks)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bharat_qrs_merchant ON offline.bharat_qrs (merchant_id, created_at DESC);

-- UPI Lite: an on-device low-value wallet holding a mutable balance.
CREATE TABLE offline.upi_lite_wallets (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    customer_ref  TEXT        NOT NULL,
    balance_minor BIGINT      NOT NULL DEFAULT 0 CHECK (balance_minor >= 0),
    currency      TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',   -- UpiLiteWalletStatus (ACTIVE | CLOSED)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ
);
CREATE INDEX idx_upi_lite_wallets_merchant ON offline.upi_lite_wallets (merchant_id);
CREATE INDEX idx_upi_lite_wallets_customer ON offline.upi_lite_wallets (merchant_id, customer_ref);

-- UPI Lite wallet movements (TOPUP | SPEND), each posted to the ledger. Append-only.
CREATE TABLE offline.upi_lite_txns (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID        NOT NULL REFERENCES offline.upi_lite_wallets (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    txn_type        TEXT        NOT NULL,   -- UpiLiteTxnType (TOPUP | SPEND)
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    ledger_entry_id UUID        NOT NULL,   -- the money movement (ledger.journal_entries.id)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_upi_lite_txns_wallet ON offline.upi_lite_txns (wallet_id, created_at);

-- UPI 123Pay feature-phone / IVR intent (CREATED -> CONFIRMED). Confirming posts money-in.
CREATE TABLE offline.pay123_intents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    payer_mobile    TEXT        NOT NULL,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'CREATED', -- Pay123Status (CREATED | CONFIRMED | FAILED)
    ledger_entry_id UUID,                   -- set on confirm
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMPTZ
);
CREATE INDEX idx_pay123_intents_merchant ON offline.pay123_intents (merchant_id, created_at DESC);

-- POS / Tap-to-Pay device registration.
CREATE TABLE offline.pos_devices (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    label       TEXT        NOT NULL,
    serial_no   TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',      -- PosDeviceStatus (ACTIVE | INACTIVE)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pos_devices_merchant ON offline.pos_devices (merchant_id, created_at DESC);

-- In-person POS captures, posted money-in to the ledger. Append-only.
CREATE TABLE offline.pos_transactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID        NOT NULL REFERENCES offline.pos_devices (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency        TEXT        NOT NULL,
    capture_method  TEXT        NOT NULL,   -- PosCaptureMethod (TAP | SWIPE | QR)
    rrn             TEXT        NOT NULL,   -- simulated acquirer retrieval reference number
    ledger_entry_id UUID        NOT NULL,   -- the money-in posting (ledger.journal_entries.id)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pos_transactions_device   ON offline.pos_transactions (device_id, created_at);
CREATE INDEX idx_pos_transactions_merchant ON offline.pos_transactions (merchant_id, created_at DESC);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE offline.bharat_qrs        ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.bharat_qrs        FORCE  ROW LEVEL SECURITY;
ALTER TABLE offline.upi_lite_wallets  ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.upi_lite_wallets  FORCE  ROW LEVEL SECURITY;
ALTER TABLE offline.upi_lite_txns     ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.upi_lite_txns     FORCE  ROW LEVEL SECURITY;
ALTER TABLE offline.pay123_intents    ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.pay123_intents    FORCE  ROW LEVEL SECURITY;
ALTER TABLE offline.pos_devices       ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.pos_devices       FORCE  ROW LEVEL SECURITY;
ALTER TABLE offline.pos_transactions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline.pos_transactions  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON offline.bharat_qrs
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON offline.upi_lite_wallets
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON offline.upi_lite_txns
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON offline.pay123_intents
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON offline.pos_devices
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON offline.pos_transactions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: wallets/intents/devices mutate (balance, status); the rest are append-only.
GRANT USAGE ON SCHEMA offline TO qeet_pay_app;
GRANT SELECT, INSERT         ON offline.bharat_qrs       TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON offline.upi_lite_wallets TO qeet_pay_app;
GRANT SELECT, INSERT         ON offline.upi_lite_txns    TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON offline.pay123_intents   TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON offline.pos_devices      TO qeet_pay_app;
GRANT SELECT, INSERT         ON offline.pos_transactions TO qeet_pay_app;
