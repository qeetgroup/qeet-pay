package com.qeetgroup.qeetpay.treasury;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SweepRuleRepository extends JpaRepository<SweepRule, UUID> {

    List<SweepRule> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<SweepRule> findByMerchantIdAndStatusOrderByCreatedAt(UUID merchantId, SweepRuleStatus status);
}
