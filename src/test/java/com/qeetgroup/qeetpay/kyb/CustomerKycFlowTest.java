package com.qeetgroup.qeetpay.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Customer-KYC flow (PRD Module 19) against real Postgres (RLS), using the sandbox KYB adapter for
 * PAN and the simulated Aadhaar-OTP path: consent → Aadhaar-OTP e-KYC → PAN → overall VERIFIED.
 */
class CustomerKycFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired CustomerKycService kyc;

    @Test
    void fullEkycVerifies() {
        UUID merchantId = newMerchant();
        CustomerKyc created = kyc.create(merchantId, "cust-1", "Asha Rao", true, "consent-doc-1");
        assertThat(created.isConsentGiven()).isTrue();
        assertThat(created.getOverallStatus()).isEqualTo(KycStatus.PENDING);

        CustomerKyc initiated = kyc.initiateAadhaar(merchantId, created.getId(), "123456789012");
        assertThat(initiated.getAadhaarLast4()).isEqualTo("9012"); // only last-4 retained
        assertThat(initiated.getAadhaarTxnId()).isNotBlank();

        CustomerKyc aadhaarDone =
                kyc.verifyAadhaar(merchantId, created.getId(), initiated.getAadhaarTxnId(), "123456");
        assertThat(aadhaarDone.getAadhaarStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(aadhaarDone.getOverallStatus()).isEqualTo(KycStatus.PENDING); // PAN still pending

        CustomerKyc done = kyc.submitPan(merchantId, created.getId(), "ABCDE1234F");
        assertThat(done.getPanStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(done.getOverallStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(done.getVerifiedAt()).isNotNull();
    }

    @Test
    void badOtpRejectsAadhaar() {
        UUID merchantId = newMerchant();
        CustomerKyc c = kyc.create(merchantId, "cust-2", "Ravi Kumar", true, null);
        CustomerKyc initiated = kyc.initiateAadhaar(merchantId, c.getId(), "123456789012");
        CustomerKyc result = kyc.verifyAadhaar(merchantId, c.getId(), initiated.getAadhaarTxnId(), "000000");
        assertThat(result.getAadhaarStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(result.getOverallStatus()).isEqualTo(KycStatus.REJECTED);
    }

    @Test
    void aadhaarRequiresConsent() {
        UUID merchantId = newMerchant();
        CustomerKyc c = kyc.create(merchantId, "cust-3", "Neha Singh", false, null);
        assertThatThrownBy(() -> kyc.initiateAadhaar(merchantId, c.getId(), "123456789012"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failPanRejectsOverall() {
        UUID merchantId = newMerchant();
        CustomerKyc c = kyc.create(merchantId, "cust-4", "Test User", true, null);
        CustomerKyc result = kyc.submitPan(merchantId, c.getId(), "fail_bad_pan"); // sandbox → REJECTED
        assertThat(result.getPanStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(result.getOverallStatus()).isEqualTo(KycStatus.REJECTED);
    }

    private UUID newMerchant() {
        return merchants.create("kyc-" + UUID.randomUUID().toString().substring(0, 8), "KYC Co")
                .merchant().getId();
    }
}
