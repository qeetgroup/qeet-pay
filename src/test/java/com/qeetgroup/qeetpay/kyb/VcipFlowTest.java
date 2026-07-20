package com.qeetgroup.qeetpay.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V-CIP session flow (PRD Module 19) against real Postgres (RLS): the SCHEDULED → IN_PROGRESS →
 * COMPLETED state machine retains only a biometric reference; FAILED purges it; illegal transitions
 * are rejected.
 */
class VcipFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired VcipService vcip;

    @Test
    void scheduleStartCompleteRetainsBiometricRef() {
        UUID merchantId = newMerchant();
        VcipSession scheduled = vcip.schedule(merchantId, "Asha Rao", "ABCDE1234F", "agent-1", null);
        assertThat(scheduled.getStatus()).isEqualTo(VcipStatus.SCHEDULED);

        vcip.start(merchantId, scheduled.getId());
        VcipSession completed =
                vcip.complete(merchantId, scheduled.getId(), "bio_ref_token_abc", 92, "12.97,77.59");
        assertThat(completed.getStatus()).isEqualTo(VcipStatus.COMPLETED);
        assertThat(completed.getStartedAt()).isNotNull();
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(completed.getBiometricRef()).isEqualTo("bio_ref_token_abc");
        assertThat(completed.getLivenessScore()).isEqualTo(92);
        assertThat(completed.getRetentionExpiresAt()).isNotNull(); // minimal-retention window set
    }

    @Test
    void failPurgesBiometrics() {
        UUID merchantId = newMerchant();
        VcipSession s = vcip.schedule(merchantId, "Ravi Kumar", null, null, null);
        vcip.start(merchantId, s.getId());
        VcipSession failed = vcip.fail(merchantId, s.getId(), "liveness check failed");
        assertThat(failed.getStatus()).isEqualTo(VcipStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("liveness check failed");
        assertThat(failed.getBiometricRef()).isNull();
        assertThat(failed.getRetentionExpiresAt()).isNull();
    }

    @Test
    void cannotCompleteBeforeStart() {
        UUID merchantId = newMerchant();
        VcipSession s = vcip.schedule(merchantId, "Neha Singh", null, null, null);
        assertThatThrownBy(() -> vcip.complete(merchantId, s.getId(), "ref", 80, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listReturnsSessionsForMerchant() {
        UUID merchantId = newMerchant();
        vcip.schedule(merchantId, "One", null, null, null);
        vcip.schedule(merchantId, "Two", null, null, null);
        assertThat(vcip.list(merchantId)).hasSize(2);
    }

    private UUID newMerchant() {
        return merchants.create("vcip-" + UUID.randomUUID().toString().substring(0, 8), "VCIP Co")
                .merchant().getId();
    }
}
