package com.qeetgroup.qeetpay.revrec;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Revenue-recognition flow (IndAS 115): creating a schedule defers the full amount
 * (settlement debit / deferred_revenue credit); recognising each period moves it into revenue, so
 * deferred_revenue nets to zero and revenue accrues to the contract total when the schedule completes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RevRecScheduleTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired MerchantService merchants;
    @Autowired RevRecService revRec;
    @Autowired LedgerService ledger;

    @Test
    void deferAndRecognizeStraightLineOverTwelveMonths() {
        UUID merchantId = newMerchant();
        LocalDate start = LocalDate.of(2026, 4, 1);

        RevRecService.ScheduleWithEntries created =
                revRec.createSchedule(
                        merchantId, "subscription", "sub_123", 120_000, "INR",
                        RecognitionMethod.STRAIGHT_LINE, start, 12);

        assertThat(created.entries()).hasSize(12);
        assertThat(created.schedule().getStatus()).isEqualTo(RecognitionStatus.SCHEDULED);
        // Deferral posted: cash in settlement, unearned in deferred_revenue.
        assertThat(balance(merchantId, "settlement")).isEqualTo(120_000);
        assertThat(balance(merchantId, "deferred_revenue")).isEqualTo(120_000);
        assertThat(balance(merchantId, "revenue")).isZero();

        // Recognise only the first period (period_end 2026-05-01).
        int firstRun = revRec.recognizeDue(merchantId, created.schedule().getId(), LocalDate.of(2026, 5, 1));
        assertThat(firstRun).isEqualTo(1);
        assertThat(balance(merchantId, "revenue")).isEqualTo(10_000);
        assertThat(balance(merchantId, "deferred_revenue")).isEqualTo(110_000);

        // Re-running for the same asOf recognises nothing new (idempotent).
        assertThat(revRec.recognizeDue(merchantId, created.schedule().getId(), LocalDate.of(2026, 5, 1)))
                .isZero();

        // Sweep everything due by the end of the term.
        int rest = revRec.recognizeAllDue(merchantId, LocalDate.of(2027, 4, 1));
        assertThat(rest).isEqualTo(11);
        assertThat(balance(merchantId, "revenue")).isEqualTo(120_000);
        assertThat(balance(merchantId, "deferred_revenue")).isZero();

        RevRecService.ScheduleWithEntries done = revRec.getSchedule(merchantId, created.schedule().getId());
        assertThat(done.schedule().getStatus()).isEqualTo(RecognitionStatus.COMPLETED);
        assertThat(done.schedule().getRecognizedMinor()).isEqualTo(120_000);
        assertThat(done.schedule().getCompletedAt()).isNotNull();
    }

    @Test
    void immediateRecognisesWholeAmountAtStart() {
        UUID merchantId = newMerchant();
        LocalDate start = LocalDate.of(2026, 4, 10);

        RevRecService.ScheduleWithEntries created =
                revRec.createSchedule(
                        merchantId, "invoice", "inv_9", 50_000, "INR",
                        RecognitionMethod.IMMEDIATE, start, 1);

        assertThat(created.entries()).hasSize(1);
        assertThat(revRec.recognizeDue(merchantId, created.schedule().getId(), start)).isEqualTo(1);
        assertThat(balance(merchantId, "revenue")).isEqualTo(50_000);
        assertThat(balance(merchantId, "deferred_revenue")).isZero();
        assertThat(revRec.getSchedule(merchantId, created.schedule().getId()).schedule().getStatus())
                .isEqualTo(RecognitionStatus.COMPLETED);
    }

    @Test
    void nothingRecognisedBeforePeriodEnd() {
        UUID merchantId = newMerchant();
        LocalDate start = LocalDate.of(2026, 4, 1);
        RevRecService.ScheduleWithEntries created =
                revRec.createSchedule(
                        merchantId, "subscription", "sub_early", 120_000, "INR",
                        RecognitionMethod.STRAIGHT_LINE, start, 12);

        assertThat(revRec.recognizeDue(merchantId, created.schedule().getId(), start.minusDays(1))).isZero();
        assertThat(balance(merchantId, "revenue")).isZero();
    }

    private UUID newMerchant() {
        return merchants.create("rr-" + UUID.randomUUID().toString().substring(0, 8), "RevRec Co")
                .merchant().getId();
    }

    private long balance(UUID merchantId, String code) {
        return ledger.balanceMinor(merchantId, ledger.accountByCode(merchantId, code).getId());
    }
}
