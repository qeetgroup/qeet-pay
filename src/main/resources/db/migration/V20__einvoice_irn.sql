-- V20 — E-invoicing / IRN (TAD §7.3, PRD Module 05, Phase 2). An issued GST invoice above the
-- e-invoicing turnover threshold must be registered with the government IRP (Invoice Registration
-- Portal), which returns an IRN (64-char invoice hash), an acknowledgement number/date, and a
-- signed QR code that is embedded on the invoice PDF. An IRN can be cancelled within 24h with a
-- reason. These are additive columns on the existing gst.gst_invoices header (which already carries
-- SELECT/INSERT/UPDATE for the app role and merchant RLS), so no new grants/policies are needed.

ALTER TABLE gst.gst_invoices
    ADD COLUMN irn               TEXT,
    ADD COLUMN irp_ack_no        TEXT,
    ADD COLUMN irp_ack_date      TIMESTAMPTZ,
    ADD COLUMN signed_qr_code    TEXT,
    ADD COLUMN irn_status        TEXT NOT NULL DEFAULT 'NONE',  -- IrnStatus (NONE | GENERATED | CANCELLED)
    ADD COLUMN irn_generated_at  TIMESTAMPTZ,
    ADD COLUMN irn_cancelled_at  TIMESTAMPTZ,
    ADD COLUMN irn_cancel_reason TEXT;

-- One IRN per invoice; enforce uniqueness within a merchant (consistent with the merchant-scoped
-- RLS and every other index here — each merchant registers under its own KYB-verified GSTIN, so a
-- global unique IRN holds in production without over-constraining the multi-tenant store).
CREATE UNIQUE INDEX idx_gst_invoices_irn ON gst.gst_invoices (merchant_id, irn) WHERE irn IS NOT NULL;
