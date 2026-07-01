package com.qeetgroup.qeetpay.gst;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantGstinRepository extends JpaRepository<MerchantGstin, UUID> {
    List<MerchantGstin> findByMerchantId(UUID merchantId);
    boolean existsByMerchantIdAndGstin(UUID merchantId, String gstin);
}
