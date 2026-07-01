package com.qeetgroup.qeetpay.billing;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and aggregates usage events for metered plans (PER_UNIT, TIERED, VOLUME, HYBRID).
 * Deduplication is enforced via the unique index on (subscription_id, idempotency_key).
 */
@Service
public class UsageMeterService {

    private final UsageEventRepository usageEvents;
    private final SubscriptionRepository subscriptions;
    private final MerchantScope merchantScope;

    public UsageMeterService(
            UsageEventRepository usageEvents,
            SubscriptionRepository subscriptions,
            MerchantScope merchantScope) {
        this.usageEvents = usageEvents;
        this.subscriptions = subscriptions;
        this.merchantScope = merchantScope;
    }

    @Transactional
    public UsageEvent ingest(UUID merchantId, UUID subscriptionId, String metricKey, long quantity, String idempotencyKey) {
        merchantScope.apply(merchantId);
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        Subscription sub = subscriptions.findById(subscriptionId)
                .filter(s -> s.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new BillingNotFoundException("no subscription " + subscriptionId));
        if (sub.getStatus() != SubscriptionStatus.ACTIVE && sub.getStatus() != SubscriptionStatus.TRIALING) {
            throw new IllegalStateException("subscription is not active; status=" + sub.getStatus());
        }
        // Pre-check idempotency key to avoid flush-time ConstraintViolation
        if (idempotencyKey != null) {
            boolean exists = usageEvents.existsBySubscriptionIdAndIdempotencyKey(subscriptionId, idempotencyKey);
            if (exists) return null;
        }
        return usageEvents.save(new UsageEvent(merchantId, subscriptionId, metricKey, quantity, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public long aggregateQuantity(UUID merchantId, UUID subscriptionId, String metricKey, Instant from, Instant to) {
        merchantScope.apply(merchantId);
        return usageEvents.sumQuantity(subscriptionId, metricKey, from, to);
    }
}
