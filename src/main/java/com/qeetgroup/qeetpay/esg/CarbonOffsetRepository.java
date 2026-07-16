package com.qeetgroup.qeetpay.esg;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CarbonOffsetRepository extends JpaRepository<CarbonOffset, UUID> {

    List<CarbonOffset> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    @Query("SELECT COALESCE(SUM(o.gramsCo2Offset), 0) FROM CarbonOffset o WHERE o.merchantId = :merchantId")
    long sumGramsOffset(UUID merchantId);
}
