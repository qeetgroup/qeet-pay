package com.qeetgroup.qeetpay.aml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlCaseRepository extends JpaRepository<AmlCase, UUID> {

    List<AmlCase> findByMerchantIdOrderByOpenedAtDesc(UUID merchantId);
}
