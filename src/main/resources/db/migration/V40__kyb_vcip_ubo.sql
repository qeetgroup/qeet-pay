-- V40 — KYB expansion (PRD Module 19 "Onboarding & Compliance", RBI Master Directions on KYC).
-- Three new merchant-scoped tables in a dedicated `kyb` schema (the original per-merchant KYB state
-- lives in platform.merchant_kyb, V13; these extend it with V-CIP, customer KYC and a UBO registry):
--
--   • kyb.vcip_sessions    — Video-based Customer Identification Process (V-CIP) sessions. State
--                            machine SCHEDULED → IN_PROGRESS → COMPLETED | FAILED. Minimal biometric
--                            retention: only a reference token + liveness score are held, purged on
--                            failure and after retention_expires_at (never raw biometrics).
--   • kyb.customer_kyc     — end-customer KYC: Aadhaar-OTP e-KYC (masked, last-4 only) + PAN + the
--                            consent flags mandated for Aadhaar authentication (simulated backend).
--   • kyb.beneficial_owners— Ultimate Beneficial Owner registry: any natural person holding > 10%
--                            equity (ownership_bps > 1000) per the RBI Master Directions on KYC.
--
-- Every table is merchant_id-scoped with the standard RLS block + least-privilege GRANTs, copied
-- from a sibling migration (see V26__escrow.sql / V13__kyb.sql).

CREATE SCHEMA IF NOT EXISTS kyb;

-- ── V-CIP sessions ──────────────────────────────────────────────────────────
CREATE TABLE kyb.vcip_sessions (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id          UUID        NOT NULL REFERENCES platform.merchants (id),
    subject_name         TEXT        NOT NULL,               -- signatory / director being verified
    subject_ref          TEXT,                               -- e.g. PAN / DIN of the subject
    status               TEXT        NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED|IN_PROGRESS|COMPLETED|FAILED
    agent_id             TEXT,                               -- RE official conducting the V-CIP
    scheduled_at         TIMESTAMPTZ,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    biometric_ref        TEXT,                               -- token/hash only; purged on fail/expiry
    liveness_score       INT,                                -- 0-100 liveness confidence
    geo_tag              TEXT,                               -- RBI V-CIP geo-tagging requirement
    retention_expires_at TIMESTAMPTZ,                        -- minimal-retention window for biometrics
    failure_reason       TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vcip_sessions_merchant ON kyb.vcip_sessions (merchant_id);

-- ── Customer KYC ────────────────────────────────────────────────────────────
CREATE TABLE kyb.customer_kyc (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id       UUID        NOT NULL REFERENCES platform.merchants (id),
    customer_ref      TEXT        NOT NULL,                  -- merchant's own customer identifier
    full_name         TEXT        NOT NULL,
    aadhaar_last4     TEXT,                                  -- masked; full Aadhaar is never stored
    aadhaar_txn_id    TEXT,                                  -- Aadhaar-OTP transaction reference
    aadhaar_status    TEXT        NOT NULL DEFAULT 'PENDING',-- PENDING|VERIFIED|REJECTED
    pan               TEXT,
    pan_status        TEXT        NOT NULL DEFAULT 'PENDING',
    consent_given     BOOLEAN     NOT NULL DEFAULT false,    -- Aadhaar-auth consent artifact captured
    consent_at        TIMESTAMPTZ,
    consent_artifact  TEXT,                                  -- reference to the stored consent record
    overall_status    TEXT        NOT NULL DEFAULT 'PENDING',
    verified_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, customer_ref)
);
CREATE INDEX idx_customer_kyc_merchant ON kyb.customer_kyc (merchant_id);

-- ── Beneficial owners (UBO registry) ────────────────────────────────────────
CREATE TABLE kyb.beneficial_owners (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id       UUID        NOT NULL REFERENCES platform.merchants (id),
    name              TEXT        NOT NULL,
    pan               TEXT,
    din               TEXT,                                  -- Director Identification Number
    nationality       TEXT        NOT NULL DEFAULT 'IN',
    ownership_bps     INT         NOT NULL CHECK (ownership_bps > 1000 AND ownership_bps <= 10000), -- > 10% per RBI
    is_control_person BOOLEAN     NOT NULL DEFAULT false,    -- control by means other than equity
    pan_status        TEXT        NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_beneficial_owners_merchant ON kyb.beneficial_owners (merchant_id);

-- ── Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
--    scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE kyb.vcip_sessions     ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyb.vcip_sessions     FORCE  ROW LEVEL SECURITY;
ALTER TABLE kyb.customer_kyc      ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyb.customer_kyc      FORCE  ROW LEVEL SECURITY;
ALTER TABLE kyb.beneficial_owners ENABLE ROW LEVEL SECURITY;
ALTER TABLE kyb.beneficial_owners FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON kyb.vcip_sessions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON kyb.customer_kyc
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON kyb.beneficial_owners
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- ── Least-privilege app role. V-CIP + customer KYC mutate in place; UBOs can be removed (a person
--    exits), so beneficial_owners also grants DELETE.
GRANT USAGE ON SCHEMA kyb TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE         ON kyb.vcip_sessions     TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE         ON kyb.customer_kyc      TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON kyb.beneficial_owners TO qeet_pay_app;
