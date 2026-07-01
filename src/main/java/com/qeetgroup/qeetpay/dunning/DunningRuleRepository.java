package com.qeetgroup.qeetpay.dunning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DunningRuleRepository extends JpaRepository<DunningRule, UUID> {
    List<DunningRule> findByMerchantIdAndActiveTrue(UUID merchantId);
}
