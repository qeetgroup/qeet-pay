package com.qeetgroup.qeetpay.paymentlinks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLinkRepository extends JpaRepository<PaymentLink, UUID> {

    List<PaymentLink> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<PaymentLink> findByMerchantIdAndCode(UUID merchantId, String code);
}
