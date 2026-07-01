package com.qeetgroup.qeetpay.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {
    List<SubscriptionEvent> findBySubscriptionIdOrderByOccurredAtAsc(UUID subscriptionId);
}
