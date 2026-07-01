package com.qeetgroup.qeetpay.mandates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qeetgroup.qeetpay.ledger.LedgerService;
import com.qeetgroup.qeetpay.merchants.MerchantService;
import java.time.LocalDate;
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

/**
 * Mandate lifecycle (TAD Module 02): create → activate → debit (ledger-posted) → pause → revoke.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MandateFlowTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired MandateService mandateService;
    @Autowired LedgerService ledger;

    @Test
    void fullLifecycle_createActivateDebitPauseRevoke() {
        UUID merchantId = newMerchant();

        // Create
        Mandate m = mandateService.create(
                merchantId, "customer@example.com", MandateType.UPI_AUTOPAY,
                50000L, "INR", MandateFrequency.MONTHLY,
                LocalDate.now(), LocalDate.now().plusYears(1));

        assertThat(m.getStatus()).isEqualTo(MandateStatus.CREATED);
        assertThat(m.getLimitMinor()).isEqualTo(50000L);

        // Activate
        Mandate activated = mandateService.activate(merchantId, m.getId(), "rzp_mandate_test_123");
        assertThat(activated.getStatus()).isEqualTo(MandateStatus.ACTIVE);
        assertThat(activated.getProviderMandateId()).isEqualTo("rzp_mandate_test_123");

        // Debit — posts balanced ledger entry
        MandateDebit debit = mandateService.debit(merchantId, m.getId(), 30000L, "Monthly subscription");
        assertThat(debit.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(debit.getLedgerEntryId()).isNotNull();

        UUID settlement = ledger.accountByCode(merchantId, "settlement").getId();
        UUID revenue    = ledger.accountByCode(merchantId, "revenue").getId();
        assertThat(ledger.balanceMinor(merchantId, settlement)).isEqualTo(30000L);
        assertThat(ledger.balanceMinor(merchantId, revenue)).isEqualTo(30000L);

        // Pause
        Mandate paused = mandateService.pause(merchantId, m.getId());
        assertThat(paused.getStatus()).isEqualTo(MandateStatus.PAUSED);

        // Cannot debit while paused
        assertThatThrownBy(() -> mandateService.debit(merchantId, m.getId(), 10000L, "test"))
                .isInstanceOf(IllegalStateException.class);

        // Revoke
        Mandate revoked = mandateService.revoke(merchantId, m.getId());
        assertThat(revoked.getStatus()).isEqualTo(MandateStatus.REVOKED);
    }

    @Test
    void debitExceedingLimitIsRejected() {
        UUID merchantId = newMerchant();
        Mandate m = mandateService.create(
                merchantId, "cust-limit@test.com", MandateType.NACH,
                10000L, "INR", MandateFrequency.WEEKLY, LocalDate.now(), null);
        mandateService.activate(merchantId, m.getId(), null);

        assertThatThrownBy(() -> mandateService.debit(merchantId, m.getId(), 10001L, "over limit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds mandate limit");
    }

    @Test
    void cannotDebitCreatedMandate() {
        UUID merchantId = newMerchant();
        Mandate m = mandateService.create(
                merchantId, "cust-notactive@test.com", MandateType.UPI_AUTOPAY,
                20000L, "INR", MandateFrequency.MONTHLY, LocalDate.now(), null);

        assertThatThrownBy(() -> mandateService.debit(merchantId, m.getId(), 5000L, "early"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void debitHistoryIsRecorded() {
        UUID merchantId = newMerchant();
        Mandate m = mandateService.create(
                merchantId, "hist@test.com", MandateType.UPI_AUTOPAY,
                100000L, "INR", MandateFrequency.MONTHLY, LocalDate.now(), null);
        mandateService.activate(merchantId, m.getId(), null);

        mandateService.debit(merchantId, m.getId(), 20000L, "cycle 1");
        mandateService.debit(merchantId, m.getId(), 20000L, "cycle 2");

        assertThat(mandateService.debitsOf(merchantId, m.getId())).hasSize(2);
    }

    private UUID newMerchant() {
        return merchants.create("mnd-" + UUID.randomUUID().toString().substring(0, 8), "Mandate Co")
                .merchant().getId();
    }
}
