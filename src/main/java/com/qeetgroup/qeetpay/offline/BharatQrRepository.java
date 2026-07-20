package com.qeetgroup.qeetpay.offline;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BharatQrRepository extends JpaRepository<BharatQr, UUID> {

    List<BharatQr> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
