package com.qeetgroup.qeetpay.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByMerchantIdAndCode(UUID merchantId, String code);

    List<Account> findByMerchantId(UUID merchantId);
}
