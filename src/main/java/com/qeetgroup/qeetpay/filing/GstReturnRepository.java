package com.qeetgroup.qeetpay.filing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstReturnRepository extends JpaRepository<GstReturn, UUID> {

    Optional<GstReturn> findByMerchantIdAndReturnTypeAndPeriod(
            UUID merchantId, GstReturnType returnType, String period);

    List<GstReturn> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
