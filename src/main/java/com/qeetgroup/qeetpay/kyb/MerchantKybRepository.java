package com.qeetgroup.qeetpay.kyb;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantKybRepository extends JpaRepository<MerchantKyb, UUID> {
    Optional<MerchantKyb> findByMerchantId(UUID merchantId);
}
