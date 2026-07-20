package com.qeetgroup.qeetpay.offline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosTransactionRepository extends JpaRepository<PosTransaction, UUID> {

    List<PosTransaction> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);

    List<PosTransaction> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
