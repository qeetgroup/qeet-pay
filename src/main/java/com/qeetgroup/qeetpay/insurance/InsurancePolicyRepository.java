package com.qeetgroup.qeetpay.insurance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, UUID> {

    List<InsurancePolicy> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
