package com.qeetgroup.qeetpay.virtualaccounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VirtualAccountCreditRepository extends JpaRepository<VirtualAccountCredit, UUID> {

    List<VirtualAccountCredit> findByVaIdOrderByCreditedAt(UUID vaId);

    Optional<VirtualAccountCredit> findByMerchantIdAndUtr(UUID merchantId, String utr);
}
