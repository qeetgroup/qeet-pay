package com.qeetgroup.qeetpay.payments;

import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Primary {@link PaymentProvider} that selects the best acquirer for each operation (TAD §7
 * orchestration). In Phase 1 routing is global: Razorpay when configured, sandbox otherwise.
 * Phase 2 will consult {@code payments.provider_configs} for per-merchant overrides.
 *
 * <p>Every provider call is recorded in {@code payments.provider_transactions} for auditing.
 */
@Primary
@Service
public class ProviderRouter implements PaymentProvider {

    private final SandboxProvider sandbox;
    private final Optional<RazorpayPaymentProvider> razorpay;
    private final ProviderTransactionRepository providerTxns;

    public ProviderRouter(
            SandboxProvider sandbox,
            Optional<RazorpayPaymentProvider> razorpay,
            ProviderTransactionRepository providerTxns) {
        this.sandbox = sandbox;
        this.razorpay = razorpay;
        this.providerTxns = providerTxns;
    }

    @Override
    public ProviderResult authorize(Payment payment, boolean simulateFailure) {
        PaymentProvider chosen = select();
        payment.setProvider(name(chosen));
        ProviderResult result = chosen.authorize(payment, simulateFailure);
        audit(payment, "AUTHORIZE", result, name(chosen));
        return result;
    }

    @Override
    public ProviderResult capture(Payment payment) {
        PaymentProvider chosen = select();
        ProviderResult result = chosen.capture(payment);
        audit(payment, "CAPTURE", result, name(chosen));
        return result;
    }

    @Override
    public ProviderResult refund(Payment payment, long amountMinor) {
        PaymentProvider chosen = select();
        ProviderResult result = chosen.refund(payment, amountMinor);
        audit(payment, "REFUND", result, name(chosen));
        return result;
    }

    private PaymentProvider select() {
        return razorpay.isPresent() ? razorpay.get() : sandbox;
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
    }
}
