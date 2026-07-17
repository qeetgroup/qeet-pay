package com.qeetgroup.qeetpay.marketplace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSellerRepository extends JpaRepository<MarketplaceSeller, UUID> {

    List<MarketplaceSeller> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<MarketplaceSeller> findByMerchantIdAndSellerRef(UUID merchantId, String sellerRef);
}
