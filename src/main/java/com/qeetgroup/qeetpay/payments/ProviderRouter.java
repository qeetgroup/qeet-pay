package com.qeetgroup.qeetpay.payments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Primary {@link PaymentProvider} that selects the best acquirer for each operation (TAD §7
 * orchestration). Selection is scorecard-driven ({@link ProviderRoutingService}, PRD Module 07.3):
 * among the available acquirers it picks the best-scoring healthy one, falling back to the default
 * preference (Razorpay when configured, else sandbox) when there is no history to route on. Every
 * call is recorded in {@code payments.provider_transactions} for auditing and fed back into the
 * chosen provider's scorecard so routing improves as volume accrues.
 */
@Primary
@Service
public class ProviderRouter implements PaymentProvider {

    private final SandboxProvider sandbox;
    private final Optional<RazorpayPaymentProvider> razorpay;
    private final ProviderTransactionRepository providerTxns;
    private final ProviderRoutingService routing;

    public ProviderRouter(
            SandboxProvider sandbox,
            Optional<RazorpayPaymentProvider> razorpay,
            ProviderTransactionRepository providerTxns,
            ProviderRoutingService routing) {
        this.sandbox = sandbox;
        this.razorpay = razorpay;
        this.providerTxns = providerTxns;
        this.routing = routing;
    }

    @Override
    public ProviderResult authorize(Payment payment, boolean simulateFailure) {
        PaymentProvider chosen = select(payment.getMerchantId());
        payment.setProvider(name(chosen));
        ProviderResult result = chosen.authorize(payment, simulateFailure);
        audit(payment, "AUTHORIZE", result, name(chosen));
        return result;
    }

    @Override
    public ProviderResult capture(Payment payment) {
        PaymentProvider chosen = select(payment.getMerchantId());
        ProviderResult result = chosen.capture(payment);
        audit(payment, "CAPTURE", result, name(chosen));
        return result;
    }

    @Override
    public ProviderResult refund(Payment payment, long amountMinor) {
        PaymentProvider chosen = select(payment.getMerchantId());
        ProviderResult result = chosen.refund(payment, amountMinor);
        audit(payment, "REFUND", result, name(chosen));
        return result;
    }

    /** The acquirers available to route across; iteration order is the default preference. */
    private Map<String, PaymentProvider> available() {
        Map<String, PaymentProvider> providers = new LinkedHashMap<>();
        razorpay.ifPresent(r -> providers.put("RAZORPAY", r));
        providers.put("SANDBOX", sandbox);
        return providers;
    }

    private PaymentProvider select(UUID merchantId) {
        Map<String, PaymentProvider> providers = available();
        String chosen = routing.chooseProviderName(merchantId, new ArrayList<>(providers.keySet()));
        return providers.getOrDefault(chosen, providers.values().iterator().next());
    }

    private String name(PaymentProvider provider) {
        return provider instanceof RazorpayPaymentProvider ? "RAZORPAY" : "SANDBOX";
    }

    private void audit(Payment payment, String operation, ProviderResult result, String providerName) {
        providerTxns.save(new ProviderTransaction(
                payment.getId(),
                payment.getMerchantId(),
                providerName,
                operation,
                result.providerPaymentId(),
                result.success(),
                result.failureReason()));
        // Feed the outcome back into the provider's scorecard so routing learns from real traffic.
        routing.recordOutcome(payment.getMerchantId(), providerName, result.success());
    }
}
