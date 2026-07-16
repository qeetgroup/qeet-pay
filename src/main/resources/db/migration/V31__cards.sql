-- V31 — Virtual cards (PRD Module 10 "Embedded Finance", TAD §5, Phase 2/3). Prepaid virtual cards
-- (employee expense cards + customer wallet cards) funded from and spent back to the merchant's
-- balance. Loading a card is money the platform now owes the holder, so it posts into an on-demand
-- card_liability account; spending reverses it. Every load/spend is an append-only card_transaction
-- carrying the ledger entry that posted it.
--
-- Ledger postings (a dedicated card_liability liability account is opened on demand, ensureAccount):
--   load  (funds onto card): debit settlement     / credit card_liability
--   spend (off card):        debit card_liability  / credit settlement
--
-- cards mutate (balance/status); card_transactions are append-only.

CREATE SCHEMA IF NOT EXISTS cards;

-- A prepaid virtual card issued to a card holder. balance_minor never goes negative.
CREATE TABLE cards.cards (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id   UUID        NOT NULL REFERENCES platform.merchants (id),
    holder_ref    TEXT        NOT NULL,
    type          TEXT        NOT NULL,   -- CardType (EXPENSE | WALLET)
    masked_pan    TEXT        NOT NULL,
    currency      TEXT        NOT NULL,
    balance_minor BIGINT      NOT NULL DEFAULT 0 CHECK (balance_minor >= 0),
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',  -- CardStatus (ACTIVE | FROZEN | CLOSED)
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ
);
CREATE INDEX idx_cards_merchant ON cards.cards (merchant_id);

-- Append-only audit of every load / spend / refund on a card, each carrying its ledger posting.
CREATE TABLE cards.card_transactions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id         UUID        NOT NULL REFERENCES cards.cards (id),
    merchant_id     UUID        NOT NULL REFERENCES platform.merchants (id),
    type            TEXT        NOT NULL,   -- CardTxnType (LOAD | SPEND | REFUND)
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    ledger_entry_id UUID        NOT NULL,   -- the ledger posting (ledger.journal_entries.id)
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_card_transactions_card ON cards.card_transactions (card_id);

-- Multi-tenant RLS, keyed on app.current_merchant_id (TAD §6.1). FORCE so the table owner is
-- scoped too; current_setting(..., true) hides rows when the merchant is unset (never errors).
ALTER TABLE cards.cards              ENABLE ROW LEVEL SECURITY;
ALTER TABLE cards.cards              FORCE  ROW LEVEL SECURITY;
ALTER TABLE cards.card_transactions  ENABLE ROW LEVEL SECURITY;
ALTER TABLE cards.card_transactions  FORCE  ROW LEVEL SECURITY;

CREATE POLICY merchant_isolation ON cards.cards
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);
CREATE POLICY merchant_isolation ON cards.card_transactions
    USING (merchant_id = current_setting('app.current_merchant_id', true)::uuid)
    WITH CHECK (merchant_id = current_setting('app.current_merchant_id', true)::uuid);

-- Least-privilege app role: cards mutate (balance/status); transactions are append-only.
GRANT USAGE ON SCHEMA cards TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON cards.cards             TO qeet_pay_app;
GRANT SELECT, INSERT         ON cards.card_transactions TO qeet_pay_app;
