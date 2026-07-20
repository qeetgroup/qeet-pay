package com.qeetgroup.qeetpay.payroll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, UUID> {

    List<PayrollBatch> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
