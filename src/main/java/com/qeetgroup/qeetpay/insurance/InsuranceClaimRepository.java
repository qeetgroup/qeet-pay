package com.qeetgroup.qeetpay.insurance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, UUID> {

    List<InsuranceClaim> findByPolicyIdOrderByCreatedAt(UUID policyId);
}
