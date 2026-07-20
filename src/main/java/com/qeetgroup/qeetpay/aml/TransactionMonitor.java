package com.qeetgroup.qeetpay.aml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The transaction-monitoring rules engine (PRD §7.7). Pure + deterministic — no Spring/DB, so it is
 * unit-testable in isolation like {@code SplitCalculator}. Given one {@link TransactionSignal} it
 * returns every rule that fires:
 *
 * <ul>
 *   <li><b>AML-STRUCT-01 — structuring</b>: a single amount sitting just below the ₹10,00,000 CTR
 *       reporting threshold (≥ 90% of it but under it), the classic "smurfing" pattern.</li>
 *   <li><b>AML-VELO-01 — velocity</b>: an abnormal trailing-24h transaction count or cumulative
 *       value.</li>
 *   <li><b>AML-GEO-01 — geographic anomaly</b>: a counterparty in an FATF high-risk / call-for-action
 *       jurisdiction.</li>
 *   <li><b>AML-MCC-01 — high-risk MCC</b>: a merchant category code associated with elevated ML/TF
 *       risk (gambling, quasi-cash/crypto, etc.).</li>
 * </ul>
 *
 * <p>All amounts are integer minor units (paise); comparisons are exact (no floats).
 */
public final class TransactionMonitor {

    /** India CTR reporting threshold: ₹10,00,000 = 1,00,000,000 paise. */
    public static final long CTR_THRESHOLD_MINOR = 100_000_000L;

    /** Structuring band floor: 90% of the CTR threshold. Amounts in [floor, threshold) are flagged. */
    public static final long STRUCTURING_FLOOR_MINOR = 90_000_000L;

    /** Trailing-24h transaction-count above which velocity fires. */
    public static final int VELOCITY_COUNT_THRESHOLD = 50;

    /** Trailing-24h cumulative amount above which velocity fires: ₹50,00,000. */
    public static final long VELOCITY_AMOUNT_THRESHOLD_MINOR = 500_000_000L;

    /** FATF high-risk / increased-monitoring jurisdictions (ISO-3166 alpha-2, sample). */
    static final Set<String> HIGH_RISK_COUNTRIES = Set.of("KP", "IR", "MM", "SY", "AF", "YE");

    /** High-risk MCCs: gambling(7995), quasi-cash/crypto(6051), dating(7273), direct-mktg(5967),
     * money-transfer(4829). */
    static final Set<Integer> HIGH_RISK_MCCS = Set.of(7995, 6051, 7273, 5967, 4829);

    private TransactionMonitor() {}

    public static List<RuleHit> evaluate(TransactionSignal s) {
        if (s == null) {
            throw new IllegalArgumentException("signal is required");
        }
        if (s.amountMinor() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        List<RuleHit> hits = new ArrayList<>();

        if (s.amountMinor() >= STRUCTURING_FLOOR_MINOR && s.amountMinor() < CTR_THRESHOLD_MINOR) {
            hits.add(
                    new RuleHit(
                            "AML-STRUCT-01",
                            "STRUCTURING",
                            70,
                            "amount " + s.amountMinor() + " paise sits just below the "
                                    + CTR_THRESHOLD_MINOR + " paise CTR reporting threshold"));
        }

        boolean countBreach = s.txnCount24h() != null && s.txnCount24h() > VELOCITY_COUNT_THRESHOLD;
        boolean amountBreach =
                s.amount24hMinor() != null && s.amount24hMinor() > VELOCITY_AMOUNT_THRESHOLD_MINOR;
        if (countBreach || amountBreach) {
            hits.add(
                    new RuleHit(
                            "AML-VELO-01",
                            "VELOCITY",
                            60,
                            "trailing-24h activity breached limits (count=" + s.txnCount24h()
                                    + ", amount=" + s.amount24hMinor() + " paise)"));
        }

        if (s.countryCode() != null
                && HIGH_RISK_COUNTRIES.contains(s.countryCode().trim().toUpperCase())) {
            hits.add(
                    new RuleHit(
                            "AML-GEO-01",
                            "GEO_ANOMALY",
                            80,
                            "counterparty in high-risk jurisdiction " + s.countryCode()));
        }

        if (s.mcc() != null && HIGH_RISK_MCCS.contains(s.mcc())) {
            hits.add(
                    new RuleHit(
                            "AML-MCC-01",
                            "HIGH_RISK_MCC",
                            55,
                            "high-risk merchant category code " + s.mcc()));
        }

        return hits;
    }
}
