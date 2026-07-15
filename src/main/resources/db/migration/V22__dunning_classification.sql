-- V22 — AI dunning: UPI-failure classification (PRD Module 04.1, TAD §5 "Dunning" Phase-2 layer).
-- The rule-based engine (V11) stays; this adds an explainable classification layer on top of the
-- SAME dunning.attempts model: a failure code is mapped to a category (insufficient funds, limit,
-- technical, risk, mandate, customer-action) which drives an adaptive retry delay + notification
-- channel. Columns are nullable and populated in-memory before the attempt's single INSERT, so the
-- append-only (SELECT/INSERT) grant on dunning.attempts is unchanged.

ALTER TABLE dunning.attempts
    ADD COLUMN failure_category        TEXT,   -- FailureCategory (INSUFFICIENT_FUNDS | LIMIT_EXCEEDED | …)
    ADD COLUMN recommended_delay_hours INT,    -- classifier's adaptive retry timing
    ADD COLUMN recommended_channels    TEXT,   -- comma-separated: WHATSAPP,SMS,EMAIL
    ADD COLUMN classification_rationale TEXT;  -- plain-English explanation (explainable dunning)
