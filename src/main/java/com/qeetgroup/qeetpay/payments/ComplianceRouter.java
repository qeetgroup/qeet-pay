package com.qeetgroup.qeetpay.payments;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Compliance-aware routing rule layer (PRD Module 07.6). A deterministic layer that sits <em>on top
 * of</em> the {@link AiProviderScorer} ranking: it does not move money, it verifies that routing a given
 * transaction will still yield a compliant GST tax invoice / e-invoice (IRN), and surfaces the reason a
 * provider was (or should not be) chosen.
 *
 * <p>Concretely it (1) validates the merchant GSTIN ({@link GstinValidator}), (2) determines the place
 * of supply — intra-state (CGST+SGST) vs inter-state (IGST) — from the supplier vs buyer state code, and
 * (3) flags a compliance risk when the GSTIN is missing/invalid, since the IRP will reject IRN generation
 * for a malformed GSTIN. Acquirer choice itself does not change GST correctness, so this layer gates and
 * explains rather than silently re-routing money.
 */
@Component
public class ComplianceRouter {

    /** Assesses the GST compliance posture for a routing decision. */
    public ComplianceAssessment assess(String gstin, String placeOfSupplyStateCode) {
        List<String> notes = new ArrayList<>();
        boolean present = gstin != null && !gstin.isBlank();
        boolean valid = present && GstinValidator.isValid(gstin);
        String supplierState = present ? GstinValidator.stateCode(gstin) : null;
        String buyerState =
                (placeOfSupplyStateCode == null || placeOfSupplyStateCode.isBlank())
                        ? null
                        : placeOfSupplyStateCode.trim();

        String supplyType = "UNKNOWN";
        boolean igst = false;
        if (supplierState != null && buyerState != null) {
            if (supplierState.equals(buyerState)) {
                supplyType = "INTRA_STATE";
            } else {
                supplyType = "INTER_STATE";
                igst = true;
            }
        }

        boolean compliant;
        if (!present) {
            compliant = false;
            notes.add(
                    "No merchant GSTIN supplied — a compliant tax invoice / e-invoice (IRN) cannot be "
                            + "issued; routing proceeds but GST compliance is unverified.");
        } else if (!valid) {
            compliant = false;
            notes.add(
                    "Merchant GSTIN failed format/checksum validation — the IRP will reject IRN "
                            + "generation; resolve the GSTIN before routing high-value B2B invoices.");
        } else {
            compliant = true;
            switch (supplyType) {
                case "INTRA_STATE" ->
                        notes.add(
                                "Intra-state supply (supplier state "
                                        + supplierState
                                        + " = place of supply) — CGST+SGST applies; GSTIN valid, invoice is compliant.");
                case "INTER_STATE" ->
                        notes.add(
                                "Inter-state supply (supplier state "
                                        + supplierState
                                        + " → place of supply "
                                        + buyerState
                                        + ") — IGST applies; GSTIN valid, invoice is compliant.");
                default ->
                        notes.add(
                                "Merchant GSTIN valid (state "
                                        + supplierState
                                        + "); place of supply not supplied, so intra/inter-state could not be determined.");
            }
        }

        return new ComplianceAssessment(
                present, valid, supplierState, buyerState, supplyType, igst, compliant, notes);
    }

    /**
     * Short single-line context (including the raw GSTIN, which the AI gateway masks) folded into the
     * scorer's feature vector so the routing recommendation is aware of the compliance posture.
     */
    public String contextString(String gstin, ComplianceAssessment a) {
        return "gstin="
                + (gstin == null || gstin.isBlank() ? "none" : gstin)
                + "; supplyType="
                + a.supplyType()
                + "; gstinValid="
                + a.gstinValid();
    }
}
