package com.qeetgroup.qeetpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    List<Refund> findByPaymentId(UUID paymentId);

    /** Correlates an inbound refund webhook to our record by the provider's refund reference. */
    Optional<Refund> findByMerchantIdAndProviderRefundId(UUID merchantId, String providerRefundId);
}
