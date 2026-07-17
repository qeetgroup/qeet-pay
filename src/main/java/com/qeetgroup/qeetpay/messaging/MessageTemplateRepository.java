package com.qeetgroup.qeetpay.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    List<MessageTemplate> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<MessageTemplate> findByMerchantIdAndTemplateKeyAndChannel(
            UUID merchantId, String templateKey, MessageChannel channel);
}
