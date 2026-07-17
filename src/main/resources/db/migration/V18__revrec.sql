-- V18 — Revenue Recognition (IndAS 115 / ASC 606; PRD Module 11, TAD §5 "RevRec", Phase 2). The
-- accrual overlay on the cash-basis billing/gst postings: cash collected upfront for a service
-- delivered over time is booked to deferred_revenue (a liability) and *earned* ratably over the
-- service period, moving it into revenue one period at a time.
--
--   deferral   (on schedule create):  debit settlement       / credit deferred_revenue
--   recognition(per period, when due): debit deferred_revenue / credit revenue
--
-- Both use accounts already in the default chart (V2). Σ(recognitions) = total, so deferred_revenue
-- nets to zero at completion and revenue accrues to the full contract value. Schedules and entries
-- both mutate (recognized_minor / status) so the app role gets UPDATE.

CREATE SCHEMA IF NOT EXISTS revrec;

-- A performance obligation: an amount to be earned over [period_start, period_end], allocated into
-- one recognition_entry per period. total_minor = Σ entries.amount_minor (exact integer minor units).
CREATE TABLE revrec.recognition_schedules (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id        UUID        NOT NULL REFERENCES platform.merchants (id),
    source_type        TEXT        NOT NULL,   -- 'subscription' | 'invoice' | 'manual'
    source_ref         TEXT,                   -- caller's external reference (e.g. subscription id)
    currency           TEXT        NOT NULL,
    total_minor        BIGINT      NOT NULL CHECK (total_minor > 0),
    recognized_minor   BIGINT      NOT NULL DEFAULT 0 CHECK (recognized_minor >= 0),
    method             TEXT        NOT NULL,   -- RecognitionMethod (STRAIGHT_LINE | IMMEDIATE)
    status             TEXT        NOT NULL,   -- RecognitionStatus (SCHEDULED | IN_PROGRESS | COMPLETED | CANCELLED)
    period_start       DATE        NOT NULL,
    period_end         DATE        NOT NULL,
    deferral_entry_id  UUID        NOT NULL,   -- the deferral posting (ledger.journal_entries.id)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ
);
CREATE INDEX idx_recognition_schedules_merchant ON revrec.recognition_schedules (merchant_id);
CREATE INDEX idx_recognition_schedules_source
    ON revrec.recognition_schedules (merchant_id, source_type, source_ref);

-- One period's slice of a schedule. Recognized independently when period_end falls due; status
-- flips PENDING -> RECOGNIZED and captures the recognition ledger entry.
CREATE TABLE revrec.recognition_entries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id     UUID        NOT NULL REFERENCES revrec.recognition_schedules (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    seq             INT         NOT NULL,   -- 0-based period index within the schedule
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    status          TEXT        NOT NULL,   -- RecognitionEntryStatus (PENDING | RECOGNIZED)
    ledger_entry_id UUID,                   -- the recognition posting (null until recognized)
    recognized_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (schedule_id, seq)
);
CREATE INDEX idx_recognition_entries_schedule ON revrec.recognition_entries (schedule_id);
CREATE INDEX idx_recognition_entries_due
    ON revrec.recognition_entries (merchant_id, status, period_end);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE revrec.recognition_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE revrec.recognition_schedules FORCE  ROW LEVEL SECURITY;
ALTER TABLE revrec.recognition_entries   ENABLE ROW LEVEL SECURITY;
ALTER TABLE revrec.recognition_entries   FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON revrec.recognition_schedules
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON revrec.recognition_entries
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: both tables mutate (recognized_minor / status).
GRANT USAGE ON SCHEMA revrec TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON revrec.recognition_schedules TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON revrec.recognition_entries   TO qeet_pay_app;
