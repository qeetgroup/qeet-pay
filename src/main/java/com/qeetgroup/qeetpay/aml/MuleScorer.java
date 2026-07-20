package com.qeetgroup.qeetpay.aml;

import java.util.ArrayList;
import java.util.List;

/**
 * Mule-account detection heuristic (PRD §7.7, §14.3). Pure + deterministic — no Spring/DB. Scores a
 * payout beneficiary from aggregated {@link BeneficiaryActivity} using classic money-mule tells:
 *
 * <ul>
 *   <li><b>Rapid pass-through</b> — nearly everything credited leaves again (outbound ≥ 90% of
 *       inbound) after only a short hold. (+40)</li>
 *   <li><b>Fan-in</b> — many distinct inbound transactions collecting into one account. (+25)</li>
 *   <li><b>Fan-out</b> — funds sprayed out across many destinations. (+25)</li>
 *   <li><b>Counterparty spread</b> — an unusually high number of distinct counterparties. (+20)</li>
 * </ul>
 *
 * <p>The ratio test uses integer arithmetic (no floats): {@code outbound·10 ≥ inbound·9} ≡
 * {@code outbound/inbound ≥ 0.9}. The score is capped at 100.
 */
public final class MuleScorer {

    /** Funds held for less than this before leaving count as "rapid" pass-through. */
    public static final long RAPID_HOLD_SECONDS = 3600;

    /** Inbound/outbound transaction count at or above which fan-in / fan-out fires. */
    public static final int FAN_THRESHOLD = 10;

    /** Distinct-counterparty count at or above which the spread signal fires. */
    public static final int DISTINCT_COUNTERPARTY_THRESHOLD = 20;

    /** Score at or above which the beneficiary is flagged as a likely mule. */
    public static final int ALERT_THRESHOLD = 60;

    private MuleScorer() {}

    public static MuleAssessment score(BeneficiaryActivity a) {
        if (a == null) {
            throw new IllegalArgumentException("activity is required");
        }
        if (a.inboundMinor() < 0 || a.outboundMinor() < 0) {
            throw new IllegalArgumentException("amounts must be non-negative");
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();

        boolean passThrough =
                a.inboundMinor() > 0
                        && a.outboundMinor() * 10 >= a.inboundMinor() * 9
                        && a.medianHoldSeconds() < RAPID_HOLD_SECONDS;
        if (passThrough) {
            score += 40;
            reasons.add(
                    "rapid pass-through: " + a.outboundMinor() + "/" + a.inboundMinor()
                            + " paise moved out within " + a.medianHoldSeconds() + "s");
        }

        if (a.inboundCount() >= FAN_THRESHOLD) {
            score += 25;
            reasons.add("fan-in: " + a.inboundCount() + " inbound transactions");
        }

        if (a.outboundCount() >= FAN_THRESHOLD) {
            score += 25;
            reasons.add("fan-out: " + a.outboundCount() + " outbound transactions");
        }

        if (a.distinctCounterparties() >= DISTINCT_COUNTERPARTY_THRESHOLD) {
            score += 20;
            reasons.add("high counterparty spread: " + a.distinctCounterparties() + " distinct");
        }

        int capped = Math.min(score, 100);
        return new MuleAssessment(
                a.beneficiaryRef(),
                capped,
                AlertSeverity.fromScore(capped),
                capped >= ALERT_THRESHOLD,
                reasons);
    }
}
