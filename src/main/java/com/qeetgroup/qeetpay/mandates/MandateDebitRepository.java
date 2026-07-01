package com.qeetgroup.qeetpay.mandates;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateDebitRepository extends JpaRepository<MandateDebit, UUID> {
    List<MandateDebit> findByMandateId(UUID mandateId);
}
