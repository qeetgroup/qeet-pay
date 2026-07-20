package com.qeetgroup.qeetpay.gst;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HsnClassificationRepository extends JpaRepository<HsnClassification, UUID> {

    Optional<HsnClassification> findByMerchantIdAndQueryHash(UUID merchantId, String queryHash);
}
