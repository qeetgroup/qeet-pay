package com.qeetgroup.qeetpay.aml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SanctionScreeningRepository extends JpaRepository<SanctionScreening, UUID> {

    List<SanctionScreening> findByMerchantIdOrderByScreenedAtDesc(UUID merchantId);
}
