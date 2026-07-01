package com.qeetgroup.qeetpay.mandates;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {
    List<Mandate> findByMerchantId(UUID merchantId);
}
