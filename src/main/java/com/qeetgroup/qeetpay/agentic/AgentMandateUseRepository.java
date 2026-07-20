package com.qeetgroup.qeetpay.agentic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMandateUseRepository extends JpaRepository<AgentMandateUse, UUID> {

    List<AgentMandateUse> findByMandateIdOrderByCreatedAt(UUID mandateId);
}
