package com.qeetgroup.qeetpay.bnpl;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BnplAgreementRepository extends JpaRepository<BnplAgreement, UUID> {

    List<BnplAgreement> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
