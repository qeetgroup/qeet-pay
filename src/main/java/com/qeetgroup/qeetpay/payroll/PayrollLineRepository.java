package com.qeetgroup.qeetpay.payroll;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID> {

    List<PayrollLine> findByBatchIdOrderByCreatedAt(UUID batchId);
}
