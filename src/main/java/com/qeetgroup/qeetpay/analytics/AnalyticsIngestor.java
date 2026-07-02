package com.qeetgroup.qeetpay.analytics;

import com.qeetgroup.qeetpay.platform.tenancy.MerchantScope;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side — records payment and subscription facts into the analytics append-only tables.
 * Callers (PaymentService, BillingService) wrap calls fail-open so a transient analytics failure
 * never rolls back a primary transaction.
 */
@Service
public class AnalyticsIngestor {

    private final PaymentAnalyticsEventRepository paymentEvents;
    private final SubscriptionAnalyticsEventRepository subscriptionEvents;
    private final MerchantScope merchantScope;

    public AnalyticsIngestor(
            PaymentAnalyticsEventRepository paymentEvents,
            SubscriptionAnalyticsEventRepository subscriptionEvents,
            MerchantScope merchantScope) {
        this.paymentEvents      = paymentEvents;
        this.subscriptionEvents = subscriptionEvents;
        this.merchantScope      = merchantScope;
    }

    @Transactional
    public void recordPayment(UUID merchantId, UUID paymentId, String eventType, long amountMinor, String method) {
        merchantScope.apply(merchantId);
        paymentEvents.save(new PaymentAnalyticsEvent(merchantId, paymentId, eventType, amountMinor, method));
    }

    @Transactional
    public void recordSubscriptionEvent(UUID merchantId, UUID subscriptionId, String eventType, long mrrDeltaMinor) {
        merchantScope.apply(merchantId);
        subscriptionEvents.save(new SubscriptionAnalyticsEvent(merchantId, subscriptionId, eventType, mrrDeltaMinor));
    }
}
