package com.qeetgroup.qeetpay.dunning;

/**
 * Payer engagement + timing signals fed into {@link AiRetryStrategy} (PRD Module 04.2 "Smart Retry" /
 * 04.5 "Explainable Dunning"). These are the features that turn a flat, category-only retry heuristic
 * into a personalised recovery plan: <em>when</em> the payer is next likely to have funds (payday), how
 * responsive they have been, and which channel / language they engage with.
 *
 * <p>None of these are money-movement inputs — they only shape <em>how</em> and <em>when</em> the payer
 * is nudged. {@code customerContactHint} may carry raw PII (phone/email); the AI gateway masks it before
 * any model call.
 *
 * @param daysUntilPayday days until the payer's next expected credit/salary date; {@code -1} = unknown
 * @param engagementScore how responsive the payer has been to past nudges, in {@code [0,1]}
 * @param preferredChannel the channel the payer engages with most (e.g. {@code "WHATSAPP"}), or null
 * @param preferredLanguage the payer's preferred language tag (e.g. {@code "hi"}, {@code "en"}), or null
 * @param customerContactHint optional free-text contact context — may contain PII; masked by the gateway
 */
public record EngagementSignals(
        int daysUntilPayday,
        double engagementScore,
        String preferredChannel,
        String preferredLanguage,
        String customerContactHint) {

    /** Neutral defaults for when no engagement history is available — payday unknown, mid engagement. */
    public static EngagementSignals unknown() {
        return new EngagementSignals(-1, 0.5, null, null, null);
    }

    /** True when the payer's next payday is known (non-negative). */
    public boolean paydayKnown() {
        return daysUntilPayday >= 0;
    }
}
