package com.qeetgroup.qeetpay.webhooks;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId);
}
