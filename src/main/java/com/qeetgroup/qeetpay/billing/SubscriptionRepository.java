package com.qeetgroup.qeetpay.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
