package com.qeetgroup.qeetpay.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, UUID> {

    Optional<InboundMessage> findByMerchantIdAndProviderMessageId(UUID merchantId, String providerMessageId);

    List<InboundMessage> findByMerchantIdOrderByReceivedAtDesc(UUID merchantId);
}
