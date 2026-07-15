package com.qeetgroup.qeetpay.dunning;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * UPI/NACH failure classifier (PRD Module 04.1) — the Phase-2 "AI dunning" layer, implemented as a
 * transparent, deterministic rule set (an ML model can replace the {@link #categorize} step later
 * without changing callers). Pure + no DB, like {@link DunningRuleEngine}: it maps a raw provider
 * failure code/message to a {@link FailureCategory} and returns an adaptive, explainable {@link
 * RetryRecommendation}. This is what turns a flat "retry every 24h × 3" policy into failure-aware
 * recovery — the mechanism behind the "~3× recovery vs. static retry" claim in the PRD.
 */
@Component
public class FailureClassifier {

    // Ordered most-specific → most-general; the first category with a keyword hit wins.
    private static final List<Rule> RULES =
            List.of(
                    new Rule(FailureCategory.INSUFFICIENT_FUNDS,
                            "insufficient", "insufficient_funds", "low balance", "not enough", "u30", "u69", "balance"),
                    new Rule(FailureCategory.LIMIT_EXCEEDED,
                            "limit", "exceeded", "per transaction", "daily limit", "amount limit", "u16"),
                    new Rule(FailureCategory.MANDATE_ISSUE,
                            "mandate", "umn", "autopay", "not registered", "revoked", "e-mandate", "no such mandate"),
                    new Rule(FailureCategory.RISK_DECLINE,
                            "risk", "fraud", "blocked", "suspected", "security", "do not honour", "do not honor"),
                    new Rule(FailureCategory.CUSTOMER_ACTION,
                            "expired", "authentication", "otp", "declined by user", "user dropped", "invalid vpa", "card expired"),
                    new Rule(FailureCategory.TECHNICAL_DECLINE,
                            "technical", "timeout", "timed out", "gateway", "system error", "unavailable", "5xx", "bt", "u66", "npci"));

    /** Classifies a raw failure code/message. Empty/blank codes → UNKNOWN. */
    public FailureCategory categorize(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return FailureCategory.UNKNOWN;
        }
        String c = failureCode.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String keyword : rule.keywords()) {
                if (c.contains(keyword)) {
                    return rule.category();
                }
            }
        }
        return FailureCategory.UNKNOWN;
    }

    /** Classifies and produces the adaptive, explainable retry recommendation for a failure code. */
    public RetryRecommendation classify(String failureCode) {
        FailureCategory category = categorize(failureCode);
        return switch (category) {
            case INSUFFICIENT_FUNDS -> new RetryRecommendation(
                    category, true, 48, "WHATSAPP,SMS",
                    "Payer had insufficient balance. Retrying in 48h aligns with typical salary/credit "
                            + "cycles, and a WhatsApp+SMS reminder nudges the payer to keep funds ready.");
            case LIMIT_EXCEEDED -> new RetryRecommendation(
                    category, true, 24, "WHATSAPP",
                    "A per-transaction or daily UPI limit was hit. Retrying after 24h lets the daily "
                            + "limit reset; a WhatsApp nudge suggests using a higher-limit account.");
            case TECHNICAL_DECLINE -> new RetryRecommendation(
                    category, true, 1, "",
                    "Transient technical/gateway decline. A prompt silent retry in ~1h usually succeeds, "
                            + "so no customer notification is sent (avoids alarming the payer needlessly).");
            case CUSTOMER_ACTION -> new RetryRecommendation(
                    category, true, 12, "WHATSAPP,SMS,EMAIL",
                    "The payment needs customer action (re-authenticate / update method). Nudge across "
                            + "WhatsApp+SMS+email, then retry in 12h once they can act.");
            case MANDATE_ISSUE -> new RetryRecommendation(
                    category, false, 0, "WHATSAPP,EMAIL",
                    "The AutoPay mandate is missing/revoked/expired. Auto-retry is pointless until the "
                            + "customer re-authorises; prompt them to set up the mandate again.");
            case RISK_DECLINE -> new RetryRecommendation(
                    category, false, 0, "EMAIL",
                    "Declined for risk/fraud reasons. Do NOT auto-retry — route to manual review and "
                            + "notify the merchant to investigate.");
            case UNKNOWN -> new RetryRecommendation(
                    category, true, 24, "EMAIL",
                    "Failure code not recognised. Falling back to the default 24h retry with an email "
                            + "reminder.");
        };
    }

    private record Rule(FailureCategory category, String... keywords) {}
}
