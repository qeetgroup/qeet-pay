package com.qeetgroup.qeetpay.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
