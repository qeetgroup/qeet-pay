package com.qeetgroup.qeetpay.billing;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    @Query("SELECT COALESCE(SUM(u.quantity), 0) FROM UsageEvent u "
            + "WHERE u.subscriptionId = :subscriptionId AND u.metricKey = :metricKey "
            + "AND u.eventTs >= :from AND u.eventTs < :to")
    long sumQuantity(UUID subscriptionId, String metricKey, Instant from, Instant to);

    boolean existsBySubscriptionIdAndIdempotencyKey(UUID subscriptionId, String idempotencyKey);
}
