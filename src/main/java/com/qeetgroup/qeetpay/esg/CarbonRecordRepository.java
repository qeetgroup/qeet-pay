package com.qeetgroup.qeetpay.esg;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CarbonRecordRepository extends JpaRepository<CarbonRecord, UUID> {

    List<CarbonRecord> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    long countByMerchantId(UUID merchantId);

    @Query("SELECT COALESCE(SUM(r.gramsCo2), 0) FROM CarbonRecord r WHERE r.merchantId = :merchantId")
    long sumGramsCo2(UUID merchantId);
}
