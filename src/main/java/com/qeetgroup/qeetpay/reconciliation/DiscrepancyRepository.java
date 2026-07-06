package com.qeetgroup.qeetpay.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, UUID> {

    List<Discrepancy> findByReconciliationId(UUID reconciliationId);
}
