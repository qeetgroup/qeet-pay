package com.qeetgroup.qeetpay.kyb;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BeneficialOwnerRepository extends JpaRepository<BeneficialOwner, UUID> {

    List<BeneficialOwner> findByMerchantIdOrderByOwnershipBpsDesc(UUID merchantId);
}
