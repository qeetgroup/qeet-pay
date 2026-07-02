package com.qeetgroup.qeetpay.analytics;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionAnalyticsEventRepository extends JpaRepository<SubscriptionAnalyticsEvent, UUID> {}
