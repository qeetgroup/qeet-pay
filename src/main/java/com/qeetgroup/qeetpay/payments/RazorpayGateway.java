package com.qeetgroup.qeetpay.payments;

import java.util.UUID;

/**
 * Thin abstraction over the Razorpay API (TAD §7.1). Separated from the provider so the real SDK
 * client can be replaced by a stub in tests without WireMock network plumbing.
 *
 * <p>All methods declare {@link Exception} — callers catch and wrap into {@link
 * PaymentProvider.ProviderResult} failures.
 */
public interface RazorpayGateway {

    /**
     * Creates a Razorpay order. Notes embed {@code merchantId} and {@code paymentId} so that
     * incoming webhooks can correlate events back to the internal payment record.
     *
     * @return Razorpay {@code order_id} (e.g. {@code order_Abc123})
     */
    String createOrder(long amountMinor, String currency, UUID merchantId, UUID paymentId)
            throws Exception;

    /**
     * Captures a previously authorized Razorpay payment.
     *
     * @param razorpayPaymentId the {@code payment_id} (e.g. {@code pay_Xyz789}) — NOT the order_id
     * @return the captured payment id
     */
    String capturePayment(String razorpayPaymentId, long amountMinor, String currency)
            throws Exception;

    /**
     * Issues a (partial) refund against a captured payment.
     *
     * @return Razorpay {@code refund_id}
     */
    String refundPayment(String razorpayPaymentId, long amountMinor) throws Exception;
}
