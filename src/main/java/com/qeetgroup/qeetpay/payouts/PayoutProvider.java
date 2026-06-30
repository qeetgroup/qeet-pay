package com.qeetgroup.qeetpay.payouts;

/** Abstraction over the disbursement rail/provider. Phase 1 ships a sandbox implementation. */
public interface PayoutProvider {

    ProviderResult process(Payout payout);

    record ProviderResult(boolean success, String providerPayoutId, String failureReason) {
        static ProviderResult ok(String providerPayoutId) {
            return new ProviderResult(true, providerPayoutId, null);
        }

        static ProviderResult failed(String reason) {
            return new ProviderResult(false, null, reason);
        }
    }
}
