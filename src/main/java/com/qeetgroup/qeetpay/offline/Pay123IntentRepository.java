package com.qeetgroup.qeetpay.offline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Pay123IntentRepository extends JpaRepository<Pay123Intent, UUID> {

    List<Pay123Intent> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
