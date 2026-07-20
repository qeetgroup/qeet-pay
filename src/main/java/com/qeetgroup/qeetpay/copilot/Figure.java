package com.qeetgroup.qeetpay.copilot;

/**
 * One cited underlying figure behind a copilot answer — the "cite the data it used" contract (PRD
 * Module 12.5 / 17). Kept deliberately generic so a single answer can cite money (paise), percentages,
 * counts, or plain text side-by-side. Computed deterministically from the analytics / reconciliation
 * reads, so it is always present regardless of whether the model or the deterministic path answered.
 *
 * @param key stable machine key, e.g. {@code "settlement_balance"}
 * @param label human label, e.g. {@code "Settlement balance"}
 * @param value the raw value — {@link Long} paise ({@code unit=paise}), {@link Double} percent, a
 *     {@link Long} count, or a {@link String}
 * @param unit one of {@code paise} | {@code percent} | {@code count} | {@code text}
 */
public record Figure(String key, String label, Object value, String unit) {

    public static Figure money(String key, String label, long paise) {
        return new Figure(key, label, paise, "paise");
    }

    public static Figure percent(String key, String label, double value) {
        return new Figure(key, label, value, "percent");
    }

    public static Figure count(String key, String label, long n) {
        return new Figure(key, label, n, "count");
    }

    public static Figure text(String key, String label, String value) {
        return new Figure(key, label, value, "text");
    }

    /** Human-readable rendering used both in narratives and in the {@code input} sent to the gateway. */
    public String display() {
        return switch (unit) {
            case "paise" -> CopilotFormat.inr(((Number) value).longValue());
            case "percent" -> CopilotFormat.pct(((Number) value).doubleValue());
            default -> String.valueOf(value);
        };
    }
}
