package com.qeetgroup.qeetpay.revrec;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionEntryRepository extends JpaRepository<RecognitionEntry, UUID> {

    List<RecognitionEntry> findByScheduleIdOrderBySeq(UUID scheduleId);

    /** PENDING entries for a schedule whose period has ended on or before {@code asOf}, oldest first. */
    List<RecognitionEntry> findByScheduleIdAndStatusAndPeriodEndLessThanEqualOrderBySeq(
            UUID scheduleId, RecognitionEntryStatus status, LocalDate asOf);

    /** All PENDING entries for a merchant due on or before {@code asOf} (cross-schedule sweep). */
    List<RecognitionEntry> findByMerchantIdAndStatusAndPeriodEndLessThanEqualOrderByPeriodEnd(
            UUID merchantId, RecognitionEntryStatus status, LocalDate asOf);
}
