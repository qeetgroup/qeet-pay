package com.qeetgroup.qeetpay.kyb;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sandbox KYB adapter — always returns VERIFIED for well-formed inputs,
 * REJECTED if the value starts with {@code "fail_"}.
 * Active whenever no live adapter bean is present.
 */
@Component
@ConditionalOnMissingBean(name = "liveKybAdapter")
public class SandboxKybAdapter implements KybVerificationAdapter {

    @Override
    public String verifyPan(String pan) {
        return pan != null && pan.startsWith("fail_") ? MerchantKyb.REJECTED : MerchantKyb.VERIFIED;
    }

    @Override
    public String verifyGstin(String gstin) {
        return gstin != null && gstin.startsWith("fail_") ? MerchantKyb.REJECTED : MerchantKyb.VERIFIED;
    }

    @Override
    public String verifyBankAccount(String accountNumber, String ifsc) {
        return accountNumber != null && accountNumber.startsWith("fail_") ? MerchantKyb.REJECTED : MerchantKyb.VERIFIED;
    }
}
