-- V13 — Merchant KYB onboarding (TAD Module 06):
--   • platform.merchant_kyb — per-merchant KYB state (PAN / GSTIN / bank account)
--   • platform.merchants gains kyb_status column

ALTER TABLE platform.merchants
    ADD COLUMN kyb_status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | VERIFIED | REJECTED
    ADD COLUMN kyb_verified_at TIMESTAMPTZ;

CREATE TABLE platform.merchant_kyb (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL UNIQUE REFERENCES platform.merchants (id),
    pan_number      TEXT,
    pan_status      TEXT        NOT NULL DEFAULT 'PENDING',   -- PENDING | VERIFIED | REJECTED
    gstin           TEXT,
    gstin_status    TEXT        NOT NULL DEFAULT 'PENDING',
    bank_account    TEXT,
    bank_ifsc       TEXT,
    bank_status     TEXT        NOT NULL DEFAULT 'PENDING',
    overall_status  TEXT        NOT NULL DEFAULT 'PENDING',
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_at     TIMESTAMPTZ
);
CREATE INDEX idx_merchant_kyb_merchant ON platform.merchant_kyb (merchant_id);

ALTER TABLE platform.merchant_kyb ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform.merchant_kyb FORCE  ROW LEVEL SECURITY;
CREATE POLICY merchant_isolation ON platform.merchant_kyb
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON platform.merchant_kyb TO qeet_pay_app;
