package com.qeetgroup.qeetpay.payments;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import java.util.UUID;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real Razorpay API client, active only when {@code qeetpay.razorpay.enabled=true}. Wraps the SDK
 * behind {@link RazorpayGateway} so tests can inject a stub without WireMock network overhead.
 *
 * <p>Order notes embed {@code merchant_id} + {@code payment_id} so incoming payment webhooks can
 * correlate events back to the internal payment record (used in M05 webhook engine).
 */
@ConditionalOnProperty(prefix = "qeetpay.razorpay", name = "enabled", havingValue = "true")
@Component
public class DefaultRazorpayGateway implements RazorpayGateway {

    private final RazorpayClient client;

    public DefaultRazorpayGateway(RazorpayProperties props) throws RazorpayException {
        this.client = new RazorpayClient(props.keyId(), props.keySecret());
    }

    @Override
    public String createOrder(long amountMinor, String currency, UUID merchantId, UUID paymentId)
            throws RazorpayException {
        JSONObject notes = new JSONObject();
        notes.put("merchant_id", merchantId.toString());
        notes.put("payment_id", paymentId.toString());

        JSONObject request = new JSONObject();
        request.put("amount", amountMinor);
        request.put("currency", currency);
        request.put("payment_capture", 0); // manual capture (TAD §7.1)
        request.put("notes", notes);

        com.razorpay.Order order = client.orders.create(request);
        return order.get("id");
    }

    @Override
    public String capturePayment(String razorpayPaymentId, long amountMinor, String currency)
            throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountMinor);
        request.put("currency", currency);
        com.razorpay.Payment captured = client.payments.capture(razorpayPaymentId, request);
        return captured.get("id");
    }

    @Override
    public String refundPayment(String razorpayPaymentId, long amountMinor) throws RazorpayException {
        JSONObject request = new JSONObject();
        request.put("amount", amountMinor);
        com.razorpay.Refund refund = client.payments.refund(razorpayPaymentId, request);
        return refund.get("id");
    }
}
