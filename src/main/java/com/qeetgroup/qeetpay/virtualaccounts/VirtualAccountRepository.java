package com.qeetgroup.qeetpay.virtualaccounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    List<VirtualAccount> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<VirtualAccount> findByMerchantIdAndCustomerRefAndStatus(
            UUID merchantId, String customerRef, VirtualAccountStatus status);
}
