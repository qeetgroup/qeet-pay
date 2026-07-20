package com.qeetgroup.qeetpay.aml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {

    List<AmlAlert> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<AmlAlert> findByMerchantIdAndStatusOrderByCreatedAtDesc(UUID merchantId, AlertStatus status);
}
