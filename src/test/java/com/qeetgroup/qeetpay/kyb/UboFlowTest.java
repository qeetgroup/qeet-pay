package com.qeetgroup.qeetpay.kyb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.AbstractIntegrationTest;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * UBO registry flow (PRD Module 19) against real Postgres (RLS): registering an owner > 10% verifies
 * PAN via the sandbox adapter; owners ≤ 10% are rejected (RBI Master Directions); removal works.
 */
class UboFlowTest extends AbstractIntegrationTest {

    @Autowired MerchantService merchants;
    @Autowired UboService ubo;

    @Test
    void addOwnerAboveThresholdVerifiesPan() {
        UUID merchantId = newMerchant();
        BeneficialOwner owner =
                ubo.add(merchantId, "Asha Rao", "ABCDE1234F", "01234567", "IN", 4000, false);
        assertThat(owner.getOwnershipBps()).isEqualTo(4000);
        assertThat(owner.getPanStatus()).isEqualTo(KycStatus.VERIFIED); // sandbox verifies
        assertThat(ubo.list(merchantId)).hasSize(1);
    }

    @Test
    void ownerAtOrBelowTenPercentIsRejected() {
        UUID merchantId = newMerchant();
        assertThatThrownBy(() -> ubo.add(merchantId, "Small Holder", "ABCDE1234F", null, "IN", 1000, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listOrdersByOwnershipDescAndRemoveWorks() {
        UUID merchantId = newMerchant();
        ubo.add(merchantId, "Minor", "ABCDE1234F", null, "IN", 1500, false);
        BeneficialOwner major = ubo.add(merchantId, "Major", "ABCDE1234F", null, "IN", 6000, true);

        assertThat(ubo.list(merchantId)).extracting(BeneficialOwner::getName).containsExactly("Major", "Minor");

        ubo.remove(merchantId, major.getId());
        assertThat(ubo.list(merchantId)).extracting(BeneficialOwner::getName).containsExactly("Minor");
    }

    private UUID newMerchant() {
        return merchants.create("ubo-" + UUID.randomUUID().toString().substring(0, 8), "UBO Co")
                .merchant().getId();
    }
}
