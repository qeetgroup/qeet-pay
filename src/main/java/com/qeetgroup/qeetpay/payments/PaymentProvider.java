package com.qeetgroup.qeetpay.payments;

/**
 * Abstraction over an external acquirer/PSP (TAD §7 orchestration). Phase 1 ships a sandbox
 * implementation; real providers (Razorpay/Cashfree as sub-merchant) arrive later.
 */
public interface PaymentProvider {

    ProviderResult authorize(Payment payment, boolean simulateFailure);

    ProviderResult capture(Payment payment);

    ProviderResult refund(Payment payment, long amountMinor);

    record ProviderResult(boolean success, String providerPaymentId, String failureReason) {
        static ProviderResult ok(String providerPaymentId) {
            return new ProviderResult(true, providerPaymentId, null);
        }

        static ProviderResult failed(String reason) {
            return new ProviderResult(false, null, reason);
        }
    }
}
