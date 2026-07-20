package com.qeetgroup.qeetpay.crossborder;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundRemittanceRepository extends JpaRepository<OutboundRemittance, UUID> {

    List<OutboundRemittance> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<OutboundRemittance> findByMerchantIdAndFinancialYear(UUID merchantId, String financialYear);
}
