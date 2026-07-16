package com.qeetgroup.qeetpay.tds;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdsDeductionRepository extends JpaRepository<TdsDeduction, UUID> {

    List<TdsDeduction> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<TdsDeduction> findByMerchantIdAndQuarterOrderByCreatedAtDesc(UUID merchantId, String quarter);
}
