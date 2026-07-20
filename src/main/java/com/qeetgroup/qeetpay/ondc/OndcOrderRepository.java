package com.qeetgroup.qeetpay.ondc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OndcOrderRepository extends JpaRepository<OndcOrder, UUID> {

    List<OndcOrder> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
