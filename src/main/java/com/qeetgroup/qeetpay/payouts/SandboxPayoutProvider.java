package com.qeetgroup.qeetpay.payouts;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic sandbox payout rail. Succeeds by default; a destination beginning with {@code fail}
 * (e.g. {@code fail@bank}) simulates a rail failure, so bulk disbursal's partial-failure handling
 * can be exercised without a live provider. No external calls.
 */
@Component
public class SandboxPayoutProvider implements PayoutProvider {

    @Override
    public ProviderResult process(Payout payout) {
        String destination = payout.getDestination();
        if (destination != null && destination.toLowerCase().startsWith("fail")) {
            return ProviderResult.failed("sandbox_simulated_failure");
        }
        return ProviderResult.ok("sbx_payout_" + UUID.randomUUID().toString().substring(0, 12));
    }
}
