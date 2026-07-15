package com.qeetgroup.qeetpay.escrow;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowEventRepository extends JpaRepository<EscrowEvent, UUID> {

    List<EscrowEvent> findByAgreementIdOrderByCreatedAt(UUID agreementId);
}
