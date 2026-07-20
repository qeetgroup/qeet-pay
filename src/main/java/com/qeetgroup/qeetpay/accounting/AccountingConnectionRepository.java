package com.qeetgroup.qeetpay.accounting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountingConnectionRepository extends JpaRepository<AccountingConnection, UUID> {

    Optional<AccountingConnection> findByMerchantIdAndTarget(UUID merchantId, AccountingTarget target);

    List<AccountingConnection> findByMerchantIdOrderByTarget(UUID merchantId);
}
