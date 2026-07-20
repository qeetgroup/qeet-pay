package com.qeetgroup.qeetpay.offline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosDeviceRepository extends JpaRepository<PosDevice, UUID> {

    List<PosDevice> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
