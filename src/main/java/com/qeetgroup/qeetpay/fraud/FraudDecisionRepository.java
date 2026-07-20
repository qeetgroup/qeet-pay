package com.qeetgroup.qeetpay.fraud;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudDecisionRepository extends JpaRepository<FraudDecisionRecord, UUID> {

    List<FraudDecisionRecord> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
