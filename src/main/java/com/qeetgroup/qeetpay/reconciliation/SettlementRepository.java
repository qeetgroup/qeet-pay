package com.qeetgroup.qeetpay.reconciliation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findByMerchantIdAndProviderAndProviderSettlementId(
            UUID merchantId, String provider, String providerSettlementId);

    List<Settlement> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
