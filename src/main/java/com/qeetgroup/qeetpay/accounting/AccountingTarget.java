package com.qeetgroup.qeetpay.accounting;

/** The external accounting system an export is directed at. */
public enum AccountingTarget {
    TALLY,
    ZOHO,
    WEBHOOK,
    SAP;

    /** Case-insensitive parse (accepts {@code tally|zoho|webhook|sap}); 400 on anything else. */
    public static AccountingTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("target is required (tally|zoho|webhook|sap)");
        }
        try {
            return AccountingTarget.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown accounting target '" + raw + "' (tally|zoho|webhook|sap)");
        }
    }
}
