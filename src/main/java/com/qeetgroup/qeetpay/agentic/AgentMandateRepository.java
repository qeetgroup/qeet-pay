package com.qeetgroup.qeetpay.agentic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMandateRepository extends JpaRepository<AgentMandate, UUID> {

    List<AgentMandate> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
