package com.qeetgroup.qeetpay.marketplace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitPaymentRepository extends JpaRepository<SplitPayment, UUID> {

    List<SplitPayment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
