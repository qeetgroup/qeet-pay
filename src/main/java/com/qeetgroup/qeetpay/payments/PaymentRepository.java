package com.qeetgroup.qeetpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /** Correlates an inbound webhook to a payment by the provider reference stored at authorize time. */
    Optional<Payment> findByMerchantIdAndProviderPaymentId(UUID merchantId, String providerPaymentId);
}
