package com.qeetgroup.qeetpay.revrec;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecognitionScheduleRepository extends JpaRepository<RecognitionSchedule, UUID> {

    List<RecognitionSchedule> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<RecognitionSchedule> findByMerchantIdAndStatusIn(UUID merchantId, List<RecognitionStatus> statuses);
}
