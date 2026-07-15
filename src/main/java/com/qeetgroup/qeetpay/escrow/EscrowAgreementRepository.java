package com.qeetgroup.qeetpay.escrow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowAgreementRepository extends JpaRepository<EscrowAgreement, UUID> {

    List<EscrowAgreement> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
