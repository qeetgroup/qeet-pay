package com.qeetgroup.qeetpay.payments;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic sandbox acquirer (TAD §11 sandbox). Authorization succeeds unless the caller
 * explicitly requests a simulated failure; capture always succeeds. No external calls.
 */
@Component
public class SandboxProvider implements PaymentProvider {

    @Override
    public ProviderResult authorize(Payment payment, boolean simulateFailure) {
        if (simulateFailure) {
            return ProviderResult.failed("simulated_failure");
        }
        return ProviderResult.ok("sbx_auth_" + token());
    }

    @Override
    public ProviderResult capture(Payment payment) {
        return ProviderResult.ok("sbx_cap_" + token());
    }

    @Override
    public ProviderResult refund(Payment payment, long amountMinor) {
        return ProviderResult.ok("sbx_refund_" + token());
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
