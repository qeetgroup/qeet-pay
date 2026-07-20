package com.qeetgroup.qeetpay.copilot;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Deterministic, locale-independent formatting for copilot narratives. Money is paise everywhere;
 * conversion to rupees for <em>display only</em> goes through {@link BigDecimal} (never {@code double})
 * per the ledger money gotcha.
 */
final class CopilotFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    private CopilotFormat() {}

    /** Formats paise as {@code ₹#,##0.00} (grouping only for display). */
    static String inr(long paise) {
        BigDecimal rupees = BigDecimal.valueOf(paise).movePointLeft(2);
        return "₹" + new DecimalFormat("#,##0.00", SYMBOLS).format(rupees);
    }

    /** Formats a percentage value (already 0–100) as {@code 0.0%}. */
    static String pct(double value) {
        return new DecimalFormat("0.0", SYMBOLS).format(value) + "%";
    }
}
