package com.qeetgroup.qeetpay.payments;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Razorpay payment provider (TAD §7.1). Active only when {@code qeetpay.razorpay.enabled=true};
 * selected by {@link ProviderRouter} ahead of {@link SandboxProvider}.
 *
 * <p>authorize() creates a Razorpay order — the returned {@code order_id} is stored as
 * {@code providerPaymentId} and surfaced to the frontend for Checkout JS initialization.
 * capture() expects the order_id at the start of the flow; once the customer pays, a webhook
 * (M05) updates {@code providerPaymentId} to the real {@code payment_id} before capture is called.
 */
@ConditionalOnProperty(prefix = "qeetpay.razorpay", name = "enabled", havingValue = "true")
@Component
public class RazorpayPaymentProvider implements PaymentProvider {

    private final RazorpayGateway gateway;

    public RazorpayPaymentProvider(RazorpayGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public ProviderResult authorize(Payment payment, boolean simulateFailure) {
        if (simulateFailure) {
            return ProviderResult.failed("simulated_failure");
        }
        try {
            String orderId = gateway.createOrder(
                    payment.getAmountMinor(),
                    payment.getCurrency(),
                    payment.getMerchantId(),
                    payment.getId());
            return ProviderResult.ok(orderId);
        } catch (Exception e) {
            return ProviderResult.failed("razorpay_order_error: " + e.getMessage());
        }
    }

    @Override
    public ProviderResult capture(Payment payment) {
        try {
            String captureId = gateway.capturePayment(
                    payment.getProviderPaymentId(),
                    payment.getAmountMinor(),
                    payment.getCurrency());
            return ProviderResult.ok(captureId);
        } catch (Exception e) {
            return ProviderResult.failed("razorpay_capture_error: " + e.getMessage());
        }
    }

    @Override
    public ProviderResult refund(Payment payment, long amountMinor) {
        try {
            String refundId = gateway.refundPayment(payment.getProviderPaymentId(), amountMinor);
            return ProviderResult.ok(refundId);
        } catch (Exception e) {
            return ProviderResult.failed("razorpay_refund_error: " + e.getMessage());
        }
    }
}
