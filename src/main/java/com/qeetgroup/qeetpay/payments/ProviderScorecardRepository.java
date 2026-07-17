package com.qeetgroup.qeetpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderScorecardRepository extends JpaRepository<ProviderScorecard, UUID> {

    Optional<ProviderScorecard> findByMerchantIdAndProvider(UUID merchantId, String provider);

    List<ProviderScorecard> findByMerchantId(UUID merchantId);
}
