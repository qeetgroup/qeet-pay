package com.qeetgroup.qeetpay.ai;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Redacts personally-identifiable / sensitive data from free text before it is handed to a model
 * (PRD §6.4 "No raw PII / PAN to the LLM"; TAD §8.3). Matches the common Indian-payments patterns —
 * PAN, Aadhaar, card-like PANs, email, phone — and replaces each with a stable token so the masked
 * text stays useful for auditing while carrying no raw identifiers.
 *
 * <p>Order matters: longer numeric identifiers (card, then Aadhaar) are masked before shorter ones
 * (phone) so a card/Aadhaar is never partially matched as a phone number.
 */
@Component
public class PiiMasker {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // 15-digit (Amex 4-6-5) or 13–16-digit (4-4-4-1..4) card numbers, optional space/hyphen groups.
    private static final Pattern CARD =
            Pattern.compile(
                    "\\b(?:\\d{4}[ -]?\\d{6}[ -]?\\d{5}"
                            + "|\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{1,4})\\b");
    // 12-digit Aadhaar, optionally grouped 4-4-4.
    private static final Pattern AADHAAR = Pattern.compile("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b");
    // Indian PAN: 5 letters, 4 digits, 1 letter.
    private static final Pattern PAN =
            Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b", Pattern.CASE_INSENSITIVE);
    // 10-digit Indian mobile (starts 6–9), optional +91 / 91 prefix; not part of a longer digit run.
    private static final Pattern PHONE =
            Pattern.compile("(?<!\\d)(?:\\+?91[\\s-]?)?[6-9]\\d{9}(?!\\d)");

    /** Returns {@code input} with every recognised PII pattern replaced by a redaction token. */
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }
        String out = input;
        out = EMAIL.matcher(out).replaceAll("[EMAIL]");
        out = CARD.matcher(out).replaceAll("[CARD]");
        out = AADHAAR.matcher(out).replaceAll("[AADHAAR]");
        out = PAN.matcher(out).replaceAll("[PAN]");
        out = PHONE.matcher(out).replaceAll("[PHONE]");
        return out;
    }
}
