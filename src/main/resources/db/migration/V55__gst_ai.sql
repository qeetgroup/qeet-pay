-- V55 — GST AI features (PRD Module 05 HSN/SAC classification + Module 06.5 Regulatory-Change Impact
-- Radar; TAD §7 + §8.2 AI Gateway / §6.4 safety matrix). Two merchant-scoped stores in the existing
-- `gst` schema, both under Row-Level Security keyed on app.current_merchant_id (like V6/V44):
--
--   * gst.hsn_classifications — cache of deterministic/AiGateway HSN/SAC classifications. The classify
--     path is deterministic, so a repeat query returns the stored result (bumping hit_count) without a
--     fresh gateway call. No raw description is stored: only query_hash (SHA-256 of the normalised
--     text) plus the result JSON, so no PII/PAN lands here.
--   * gst.regulatory_changes — announced-but-not-yet-effective GST changes (rate change for an HSN/SAC,
--     effective date). The radar forecasts their impact over the merchant's existing gst.gst_invoices.
--
-- Every classification/forecast also writes an append-only ai.ai_decision row + ai.decision.recorded
-- outbox event through the AiGateway (V44); ai_decision_id below is a soft (cross-schema) reference.

CREATE SCHEMA IF NOT EXISTS gst;

-- HSN/SAC classification cache (one row per merchant + normalised query).
CREATE TABLE gst.hsn_classifications (
    id                UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id       UUID             NOT NULL REFERENCES platform.merchants (id),
    query_hash        TEXT             NOT NULL,   -- SHA-256 of normalised description (lookup key, no raw PII)
    result_json       TEXT             NOT NULL,   -- serialised ClassificationResult (ranked suggestions + meta)
    primary_hsn_sac   TEXT             NOT NULL,   -- top suggestion, denormalised for querying
    primary_gst_rate  INT              NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    requires_review   BOOLEAN          NOT NULL DEFAULT false,  -- low-confidence → human review (§6.4)
    ai_decision_id    UUID,                        -- soft ref to ai.ai_decision (no hard cross-schema FK)
    hit_count         BIGINT           NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (merchant_id, query_hash)
);
CREATE INDEX idx_hsn_classifications_merchant ON gst.hsn_classifications (merchant_id, updated_at DESC);

-- Announced-but-not-yet-effective GST changes tracked by the Impact Radar.
CREATE TABLE gst.regulatory_changes (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id    UUID        NOT NULL REFERENCES platform.merchants (id),
    hsn_sac        TEXT        NOT NULL,
    change_type    TEXT        NOT NULL DEFAULT 'RATE_CHANGE',  -- RegChangeType
    old_rate_pct   INT,                                          -- nullable: pre-change rate if known
    new_rate_pct   INT         NOT NULL CHECK (new_rate_pct >= 0),
    effective_date DATE        NOT NULL,
    title          TEXT        NOT NULL,
    source         TEXT,                                         -- e.g. "GST Council 53rd meeting"
    announced_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_regulatory_changes_merchant ON gst.regulatory_changes (merchant_id, effective_date);
CREATE INDEX idx_regulatory_changes_hsn ON gst.regulatory_changes (merchant_id, hsn_sac);

-- Multi-tenant RLS (TAD §6.1). FORCE so the table owner is scoped too; current_setting(..., true)
-- hides rows when the merchant is unset (never errors).
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['hsn_classifications','regulatory_changes'] LOOP
        EXECUTE format('ALTER TABLE gst.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE gst.%I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY merchant_isolation ON gst.%I '
            || 'USING (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid) '
            || 'WITH CHECK (merchant_id = current_setting(''app.current_merchant_id'', true)::uuid)', t);
    END LOOP;
END $$;

-- Least-privilege app role (created NOSUPERUSER in V2). Cache rows are updated (hit_count); reg-changes
-- are append-only in practice but granted UPDATE for symmetry with the rest of the gst schema.
GRANT USAGE ON SCHEMA gst TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON gst.hsn_classifications TO qeet_pay_app;
GRANT SELECT, INSERT, UPDATE ON gst.regulatory_changes TO qeet_pay_app;
