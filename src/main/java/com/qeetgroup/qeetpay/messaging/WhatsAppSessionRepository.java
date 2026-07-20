package com.qeetgroup.qeetpay.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, UUID> {

    Optional<WhatsAppSession> findByMerchantIdAndWaPhone(UUID merchantId, String waPhone);

    List<WhatsAppSession> findByMerchantIdOrderByUpdatedAtDesc(UUID merchantId);
}
