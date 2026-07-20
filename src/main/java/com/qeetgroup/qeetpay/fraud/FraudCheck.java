package com.qeetgroup.qeetpay.fraud;

import java.util.UUID;

/** Inputs to a fraud check (mirrors the fraud-svc {@code /score} request). */
public record FraudCheck(
        UUID merchantId,
        UUID paymentId,
        long amountMinor,
        String currency,
        String method,
        String customerVpa,
        String ip) {

    /** Backward-compatible constructor without a client IP (callers that don't capture one). */
    public FraudCheck(
            UUID merchantId,
            UUID paymentId,
            long amountMinor,
            String currency,
            String method,
            String customerVpa) {
        this(merchantId, paymentId, amountMinor, currency, method, customerVpa, null);
    }
}
