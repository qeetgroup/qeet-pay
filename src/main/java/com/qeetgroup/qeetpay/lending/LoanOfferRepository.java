package com.qeetgroup.qeetpay.lending;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanOfferRepository extends JpaRepository<LoanOffer, UUID> {

    List<LoanOffer> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
