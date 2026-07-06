package com.qeetgroup.qeetpay.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, UUID> {

    List<SettlementItem> findBySettlementId(UUID settlementId);

    long countByMerchantIdAndPaymentId(UUID merchantId, UUID paymentId);
}
