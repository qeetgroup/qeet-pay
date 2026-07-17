package com.qeetgroup.qeetpay.marketplace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitItemRepository extends JpaRepository<SplitItem, UUID> {

    List<SplitItem> findBySplitId(UUID splitId);

    List<SplitItem> findByMerchantIdAndSellerId(UUID merchantId, UUID sellerId);
}
