package com.qeetgroup.qeetpay.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A provider settlement report handed to {@link SettlementService#ingest} — the parsed shape of a
 * PA settlement file (e.g. Razorpay's amount/fee/tax/net columns). {@code reportedNetMinor} is an
 * optional batch-level control total that reconciliation checks against the sum of the lines.
 */
public record SettlementReport(
        String provider,
        String providerSettlementId,
        String currency,
        Instant settledAt,
        Long reportedNetMinor,
        List<Line> items) {

    /** One settled payment line. {@code net = gross - fee - tax}. */
    public record Line(
            UUID paymentId,
            String providerPaymentId,
            long grossMinor,
            long feeMinor,
            long taxMinor) {

        public long netMinor() {
            return grossMinor - feeMinor - taxMinor;
        }
    }
}
