-- V43 — AML / CFT (PRD §7.7, Phase 2). Anti-Money-Laundering and Combating the Financing of
-- Terrorism: sanctions/PEP screening, deterministic transaction monitoring, mule-account detection,
-- and Suspicious Transaction Report (STR) generation/filing with FIU-IND under PMLA.
--
-- Four tables, all merchant-scoped:
--   sanction_screenings  append-only log of every party screened (OFAC/UN/PEP), with the raw hits.
--   cases                investigation cases grouping alerts; mutate (status / disposition).
--   alerts               rule/screening/mule alerts; mutate (status / case_id).
--   str_reports          FIU-IND-style STRs; mutate (DRAFT -> FILED + reference id).
--
-- No money moves here (monitoring only), so there is no ledger posting. Amounts referenced in alert
-- detail are integer minor units (paise). JSON blobs (matches / detail / payload) are stored as TEXT,
-- like platform.outbox_event.payload.

CREATE SCHEMA IF NOT EXISTS aml;

-- Every sanctions/PEP screen of a party. Append-only.
CREATE TABLE aml.sanction_screenings (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    party_type    TEXT        NOT NULL,        -- PartyType (INDIVIDUAL | BUSINESS | BENEFICIARY)
    party_name    TEXT        NOT NULL,
    identifier    TEXT,                        -- PAN / GSTIN / account, if supplied
    result        TEXT        NOT NULL,        -- ScreeningResult (CLEAR | HIT)
    match_count   INT         NOT NULL DEFAULT 0 CHECK (match_count >= 0),
    risk_score    INT         NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    matches       TEXT,                        -- JSON array of watchlist hits
    screened_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_aml_screenings_merchant ON aml.sanction_screenings (merchant_id);
CREATE INDEX idx_aml_screenings_result   ON aml.sanction_screenings (merchant_id, result);

-- Investigation cases grouping one or more alerts. Mutate (status / disposition / counts).
CREATE TABLE aml.cases (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    subject       TEXT        NOT NULL,
    description   TEXT,
    status        TEXT        NOT NULL DEFAULT 'OPEN',   -- CaseStatus (OPEN | CLOSED)
    disposition   TEXT,                                  -- CaseDisposition, set on close
    risk_score    INT         NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    alert_count   INT         NOT NULL DEFAULT 0 CHECK (alert_count >= 0),
    opened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ
);
CREATE INDEX idx_aml_cases_merchant ON aml.cases (merchant_id);
CREATE INDEX idx_aml_cases_status   ON aml.cases (merchant_id, status);

-- Alerts from screening / monitoring / mule detection. Mutate (status / case_id).
CREATE TABLE aml.alerts (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    subject_ref     TEXT        NOT NULL,      -- party / beneficiary / transaction the alert is about
    transaction_ref TEXT,
    rule_code       TEXT        NOT NULL,      -- e.g. AML-STRUCT-01 / AML-SANCT-01 / AML-MULE-01
    category        TEXT        NOT NULL,      -- STRUCTURING | VELOCITY | GEO_ANOMALY | ...
    risk_score      INT         NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    severity        TEXT        NOT NULL,      -- AlertSeverity (LOW | MEDIUM | HIGH | CRITICAL)
    detail          TEXT,                      -- JSON object with rule-specific detail
    status          TEXT        NOT NULL DEFAULT 'OPEN',  -- AlertStatus (OPEN | DISMISSED | ESCALATED)
    case_id         UUID        REFERENCES aml.cases (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_aml_alerts_merchant ON aml.alerts (merchant_id);
CREATE INDEX idx_aml_alerts_status   ON aml.alerts (merchant_id, status);
CREATE INDEX idx_aml_alerts_case     ON aml.alerts (merchant_id, case_id);

-- FIU-IND-style STRs (PMLA). Mutate (DRAFT -> FILED + fiu_reference_id).
CREATE TABLE aml.str_reports (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id      UUID        NOT NULL REFERENCES platform.merchants (id),
    case_id          UUID        REFERENCES aml.cases (id),
    subject          TEXT        NOT NULL,
    grounds          TEXT        NOT NULL,     -- grounds of suspicion
    status           TEXT        NOT NULL DEFAULT 'DRAFT',  -- StrStatus (DRAFT | FILED)
    payload          TEXT        NOT NULL,     -- FIU-IND-style report body as JSON
    fiu_reference_id TEXT,                     -- assigned on filing
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    filed_at         TIMESTAMPTZ
);
CREATE INDEX idx_aml_str_merchant ON aml.str_reports (merchant_id);
CREATE INDEX idx_aml_str_status   ON aml.str_reports (merchant_id, status);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is scoped
-- too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE aml.sanction_screenings ENABLE ROW LEVEL SECURITY;
ALTER TABLE aml.sanction_screenings FORCE  ROW LEVEL SECURITY;
ALTER TABLE aml.cases               ENABLE ROW LEVEL SECURITY;
ALTER TABLE aml.cases               FORCE  ROW LEVEL SECURITY;
ALTER TABLE aml.alerts              ENABLE ROW LEVEL SECURITY;
ALTER TABLE aml.alerts              FORCE  ROW LEVEL SECURITY;
ALTER TABLE aml.str_reports         ENABLE ROW LEVEL SECURITY;
ALTER TABLE aml.str_reports         FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON aml.sanction_screenings
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON aml.cases
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON aml.alerts
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON aml.str_reports
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: screenings are append-only; cases / alerts / STRs mutate.
GRANT USAGE ON SCHEMA aml TO qeet_pay_app;
GRANT SELECT, INSERT         ON aml.sanction_screenings TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON aml.cases               TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON aml.alerts              TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON aml.str_reports         TO qeet_pay_app;
