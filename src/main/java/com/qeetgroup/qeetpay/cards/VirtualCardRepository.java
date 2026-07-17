package com.qeetgroup.qeetpay.cards;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualCardRepository extends JpaRepository<VirtualCard, UUID> {

    List<VirtualCard> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
