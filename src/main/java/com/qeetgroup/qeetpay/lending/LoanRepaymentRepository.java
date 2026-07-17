package com.qeetgroup.qeetpay.lending;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findByLoanIdOrderByCreatedAt(UUID loanId);
}
