package com.qeetgroup.qeetpay.kyb;

import static org.assertj.core.api.Assertions.assertThat;

import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** KYB verification flow using the SandboxKybAdapter (always-VERIFIED unless "fail_" prefix). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class KybFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired KybService kybService;

    @Test
    void fullVerificationFlow() {
        UUID merchantId = newMerchant();

        MerchantKyb afterPan = kybService.submitPan(merchantId, "ABCDE1234F");
        assertThat(afterPan.getPanStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(afterPan.getOverallStatus()).isEqualTo(MerchantKyb.PENDING); // not all done yet

        MerchantKyb afterGstin = kybService.submitGstin(merchantId, "27ABCDE1234F1Z5");
        assertThat(afterGstin.getGstinStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(afterGstin.getOverallStatus()).isEqualTo(MerchantKyb.PENDING);

        MerchantKyb afterBank = kybService.submitBank(merchantId, "9876543210", "HDFC0001234");
        assertThat(afterBank.getBankStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(afterBank.getOverallStatus()).isEqualTo(MerchantKyb.VERIFIED);
        assertThat(afterBank.getVerifiedAt()).isNotNull();
    }

    @Test
    void failedPanRejectedOverall() {
        UUID merchantId = newMerchant();
        MerchantKyb result = kybService.submitPan(merchantId, "fail_invalid_pan");
        assertThat(result.getPanStatus()).isEqualTo(MerchantKyb.REJECTED);
        assertThat(result.getOverallStatus()).isEqualTo(MerchantKyb.REJECTED);
    }

    @Test
    void failedGstinRejectedOverall() {
        UUID merchantId = newMerchant();
        kybService.submitPan(merchantId, "ABCDE1234F");
        MerchantKyb result = kybService.submitGstin(merchantId, "fail_invalid_gstin");
        assertThat(result.getGstinStatus()).isEqualTo(MerchantKyb.REJECTED);
        assertThat(result.getOverallStatus()).isEqualTo(MerchantKyb.REJECTED);
    }

    @Test
    void statusReturnsPendingForNewMerchant() {
        UUID merchantId = newMerchant();
        MerchantKyb kyb = kybService.status(merchantId);
        assertThat(kyb.getOverallStatus()).isEqualTo(MerchantKyb.PENDING);
        assertThat(kyb.getPanStatus()).isEqualTo(MerchantKyb.PENDING);
    }

    @Test
    void failedBankRejectedOverall() {
        UUID merchantId = newMerchant();
        kybService.submitPan(merchantId, "VALID1234F");
        kybService.submitGstin(merchantId, "27VALID1234F1Z5");
        MerchantKyb result = kybService.submitBank(merchantId, "fail_closed_account", "HDFC0001");
        assertThat(result.getBankStatus()).isEqualTo(MerchantKyb.REJECTED);
        assertThat(result.getOverallStatus()).isEqualTo(MerchantKyb.REJECTED);
    }

    private UUID newMerchant() {
        return merchants.create("kyb-" + UUID.randomUUID().toString().substring(0, 8), "KYB Co")
                .merchant().getId();
    }
}
