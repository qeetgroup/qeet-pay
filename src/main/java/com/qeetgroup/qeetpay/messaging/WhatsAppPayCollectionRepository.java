package com.qeetgroup.qeetpay.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppPayCollectionRepository extends JpaRepository<WhatsAppPayCollection, UUID> {

    List<WhatsAppPayCollection> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
