package com.qeetgroup.qeetpay.fraud;

import java.util.UUID;

/** Inputs to a fraud check (mirrors the fraud-svc {@code /score} request). */
public record FraudCheck(
        UUID merchantId,
        UUID paymentId,
        long amountMinor,
        String currency,
        String method,
        String customerVpa) {}
