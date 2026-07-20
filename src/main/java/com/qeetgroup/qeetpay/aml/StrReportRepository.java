package com.qeetgroup.qeetpay.aml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrReportRepository extends JpaRepository<StrReport, UUID> {

    List<StrReport> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
