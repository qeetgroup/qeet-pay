package com.qeetgroup.qeetpay.lending;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
