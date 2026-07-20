package com.qeetgroup.qeetpay.ai;

import java.util.Locale;

/**
 * Known AI features and whether each is <em>money-affecting</em> (PRD §6.2 / §6.4, TAD §8.1).
 * Money-affecting features recommend an action that moves or withholds money (fraud block/challenge,
 * dunning stop/continue, routing, lending underwriting, tax structuring) and therefore require a
 * human-in-the-loop review before the gateway will treat a model result as actionable; everything
 * else is advisory (classification, forecasting, natural-language queries).
 *
 * <p>The gateway accepts an arbitrary feature string so new features need no code change here — this
 * enum only provides {@link #isMoneyAffecting(String)} as a safe default so a caller that forgets to
 * flag a well-known money-affecting feature is still gated correctly.
 */
public enum AiFeature {
    FRAUD_SCORING(true),
    DUNNING_CLASSIFICATION(true),
    ORCHESTRATION_ROUTING(true),
    COMPLIANCE_ROUTING(true),
    LENDING_DECISION(true),
    TAX_OPTIMIZER(true),
    GST_CLASSIFICATION(false),
    CASHFLOW_FORECAST(false),
    RECONCILIATION_COPILOT(false),
    TREASURY_COPILOT(false),
    NLQ(false);

    private final boolean moneyAffecting;

    AiFeature(boolean moneyAffecting) {
        this.moneyAffecting = moneyAffecting;
    }

    public boolean moneyAffecting() {
        return moneyAffecting;
    }

    /** Canonical string key, e.g. {@code LENDING_DECISION} → {@code "lending.decision"}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '.');
    }

    /**
     * True if {@code feature} names a known money-affecting feature. Accepts either the enum name or
     * the dotted/hyphenated key form ({@code "lending.decision"}, {@code "lending-decision"}).
     */
    public static boolean isMoneyAffecting(String feature) {
        if (feature == null || feature.isBlank()) {
            return false;
        }
        String norm = feature.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        for (AiFeature f : values()) {
            if (f.name().equals(norm)) {
                return f.moneyAffecting;
            }
        }
        return false;
    }
}
