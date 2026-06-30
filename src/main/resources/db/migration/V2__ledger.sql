-- V2 — The double-entry ledger (TAD §6.2, §7.1): merchant-scoped accounts and append-only
-- journal entries/lines. Three guarantees are enforced at the database layer:
--   1. Balance     — each entry's lines net to zero (deferred constraint trigger, checked at COMMIT).
--   2. Tenant RLS  — every row keyed on app.current_merchant_id (FORCE, so even the owner is scoped).
--   3. Append-only — the least-privilege app role may SELECT/INSERT only; never UPDATE/DELETE.

CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.accounts (
    id          UUID        PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    code        TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    type        TEXT        NOT NULL,   -- AccountType (SETTLEMENT, REVENUE, …)
    currency    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, code)
);
CREATE INDEX idx_accounts_merchant ON ledger.accounts (merchant_id);

CREATE TABLE ledger.journal_entries (
    id          UUID        PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES platform.merchants (id),
    description TEXT        NOT NULL,
    currency    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_merchant ON ledger.journal_entries (merchant_id);

CREATE TABLE ledger.journal_lines (
    id           UUID        PRIMARY KEY,
    entry_id     UUID        NOT NULL REFERENCES ledger.journal_entries (id),
    merchant_id  UUID        NOT NULL REFERENCES platform.merchants (id),
    account_id   UUID        NOT NULL REFERENCES ledger.accounts (id),
    direction    TEXT        NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lines_entry ON ledger.journal_lines (entry_id);
CREATE INDEX idx_lines_account ON ledger.journal_lines (account_id);

-- (1) Balance invariant. Deferred so all lines of an entry are inserted before the check fires.
CREATE FUNCTION ledger.assert_entry_balanced() RETURNS trigger AS $$
BEGIN
    IF (SELECT COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0)
          FROM ledger.journal_lines WHERE entry_id = NEW.entry_id) <> 0 THEN
        RAISE EXCEPTION 'journal entry % is unbalanced', NEW.entry_id USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_entry_balanced
    AFTER INSERT ON ledger.journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger.assert_entry_balanced();

-- (2) Multi-tenant RLS backbone (TAD §6.1). current_setting(..., true) => unset merchant yields
-- NULL (rows hidden), never an error.
ALTER TABLE ledger.accounts        ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.accounts        FORCE  ROW LEVEL SECURITY;
ALTER TABLE ledger.journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.journal_entries FORCE  ROW LEVEL SECURITY;
ALTER TABLE ledger.journal_lines   ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger.journal_lines   FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON ledger.accounts
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON ledger.journal_entries
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON ledger.journal_lines
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- (3) Least-privilege application role. NOSUPERUSER so RLS + the append-only grants actually bite
-- (a superuser bypasses both). NOLOGIN here; grant LOGIN + a password for a real deployment. This
-- is the role RlsIsolationTest / LedgerBalanceTest SET ROLE to.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'qeet_pay_app') THEN
        CREATE ROLE qeet_pay_app NOSUPERUSER NOLOGIN;
    END IF;
END $$;

GRANT USAGE ON SCHEMA platform, ledger TO qeet_pay_app;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA ledger TO qeet_pay_app;          -- append-only ledger
GRANT SELECT, INSERT ON platform.merchants, platform.api_keys TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON platform.idempotency_keys, platform.outbox_event TO qeet_pay_app;
