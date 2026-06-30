package com.qeetgroup.qeetpay.payouts;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Deterministic sandbox payout rail — always succeeds. No external calls. */
@Component
public class SandboxPayoutProvider implements PayoutProvider {

    @Override
    public ProviderResult process(Payout payout) {
        return ProviderResult.ok("sbx_payout_" + UUID.randomUUID().toString().substring(0, 12));
    }
}
