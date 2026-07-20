package com.qeetgroup.qeetpay.payments;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure, self-contained GSTIN validator used by the compliance-aware routing layer (PRD Module 07.6).
 * Deterministic — no Spring/DB — so it is unit-testable in isolation, mirroring {@code ProviderScorer}
 * and {@code gst.GstCalculator}.
 *
 * <p>A GSTIN is 15 characters: a 2-digit state code, a 10-character PAN, a 1-character entity number, a
 * fixed {@code 'Z'}, and a base-36 checksum digit. This class validates both the structural format and
 * the checksum (the GSTN modulo-36 algorithm) and exposes the state code so routing can determine the
 * place of supply (intra- vs inter-state).
 */
public final class GstinValidator {

    private static final String BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    // 2-digit state + 10-char PAN (5 letters, 4 digits, 1 letter) + entity + 'Z' + checksum.
    private static final Pattern FORMAT =
            Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private GstinValidator() {}

    /** True when {@code gstin} is well-formed <em>and</em> its checksum digit is correct. */
    public static boolean isValid(String gstin) {
        if (gstin == null) {
            return false;
        }
        String g = gstin.trim().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(g).matches()) {
            return false;
        }
        return g.charAt(14) == checkDigit(g);
    }

    /** The 2-digit state code (e.g. {@code "27"} = Maharashtra), or null when not extractable. */
    public static String stateCode(String gstin) {
        if (gstin == null) {
            return null;
        }
        String g = gstin.trim();
        return g.length() >= 2 ? g.substring(0, 2) : null;
    }

    /** GSTN modulo-36 checksum over the first 14 characters. */
    private static char checkDigit(String g) {
        int factor = 2;
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int code = BASE36.indexOf(g.charAt(i));
            int digit = code * factor;
            sum += (digit / 36) + (digit % 36);
            factor = (factor == 2) ? 1 : 2;
        }
        int checkCode = (36 - (sum % 36)) % 36;
        return BASE36.charAt(checkCode);
    }
}
