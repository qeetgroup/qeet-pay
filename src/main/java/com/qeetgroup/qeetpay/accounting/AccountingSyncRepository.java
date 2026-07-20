package com.qeetgroup.qeetpay.accounting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingSyncRepository extends JpaRepository<AccountingSync, UUID> {

    /** A merchant's export runs, newest first. */
    List<AccountingSync> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
