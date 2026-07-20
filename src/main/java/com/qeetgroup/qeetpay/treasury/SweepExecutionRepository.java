package com.qeetgroup.qeetpay.treasury;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SweepExecutionRepository extends JpaRepository<SweepExecution, UUID> {

    List<SweepExecution> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<SweepExecution> findByRuleIdOrderByCreatedAtDesc(UUID ruleId);
}
