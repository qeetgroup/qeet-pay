package com.qeetgroup.qeetpay.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiDecisionRepository extends JpaRepository<AiDecision, UUID> {

    List<AiDecision> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
