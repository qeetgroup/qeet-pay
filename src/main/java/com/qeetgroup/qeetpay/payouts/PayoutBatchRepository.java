package com.qeetgroup.qeetpay.payouts;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID> {

    List<PayoutBatch> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
