package com.qeetgroup.qeetpay.kyb;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerKycRepository extends JpaRepository<CustomerKyc, UUID> {

    List<CustomerKyc> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<CustomerKyc> findByMerchantIdAndCustomerRef(UUID merchantId, String customerRef);
}
