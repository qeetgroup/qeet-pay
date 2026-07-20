package com.qeetgroup.qeetpay.payments;

import java.util.List;

/**
 * The GST compliance posture of a routing decision (PRD Module 07.6 "Compliance-aware routing"). A
 * deterministic assessment of whether a compliant tax invoice / e-invoice (IRN) can be issued for the
 * transaction, computed from the merchant's GSTIN and the buyer's place of supply.
 *
 * @param gstinPresent whether a merchant GSTIN was supplied at all
 * @param gstinValid whether that GSTIN passed format + checksum validation ({@link GstinValidator})
 * @param supplierStateCode the supplier's 2-digit state code, derived from the GSTIN
 * @param placeOfSupplyStateCode the buyer's 2-digit place-of-supply state code (as supplied)
 * @param supplyType {@code "INTRA_STATE"} (CGST+SGST), {@code "INTER_STATE"} (IGST), or {@code "UNKNOWN"}
 * @param igstApplicable whether the supply attracts IGST (inter-state)
 * @param compliant whether routing can proceed without a compliance warning
 * @param notes plain-English compliance notes surfaced in the routing explanation
 */
public record ComplianceAssessment(
        boolean gstinPresent,
        boolean gstinValid,
        String supplierStateCode,
        String placeOfSupplyStateCode,
        String supplyType,
        boolean igstApplicable,
        boolean compliant,
        List<String> notes) {}
